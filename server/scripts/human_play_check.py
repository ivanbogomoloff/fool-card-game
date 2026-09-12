#!/usr/bin/env python3
"""3× simclient --mode human через PTY; ходы меню 8/9/10; проверка логов."""

from __future__ import annotations

import errno
import os
import pty
import random
import re
import select
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
LOGS = ROOT / "logs"

RANK = {
    "6": 6,
    "7": 7,
    "8": 8,
    "9": 9,
    "10": 10,
    "Валет": 11,
    "Дама": 12,
    "Король": 13,
    "Туз": 14,
}
SUIT = {"♠": "S", "♥": "H", "♦": "D", "♣": "C"}


@dataclass
class Card:
    rank: int
    suit: str
    raw: str
    idx: int


@dataclass
class Pair:
    idx: int
    attack: Card
    defense: Card | None


@dataclass
class Snapshot:
    hand: list[Card] = field(default_factory=list)
    pairs: list[Pair] = field(default_factory=list)
    can_pass: bool = False
    can_bito: bool = False
    can_take: bool = False
    phase: str = ""
    trump: str | None = None


class HumanClient:
    def __init__(self, name: str, addr: str):
        self.name = name
        self.buf = ""
        self.all: list[str] = []
        self.game_id = ""
        self.code = ""
        self.account_id = ""
        self.finished = False
        self.errors: list[str] = []
        self.moves: list[str] = []
        self.master, slave = pty.openpty()
        env = os.environ.copy()
        env["CGO_ENABLED"] = "0"
        self.proc = subprocess.Popen(
            [
                "go",
                "run",
                "./cmd/simclient",
                "--mode",
                "human",
                "--addr",
                addr,
                "--name",
                name,
            ],
            cwd=str(SRC),
            stdin=slave,
            stdout=slave,
            stderr=slave,
            env=env,
            close_fds=True,
        )
        os.close(slave)
        # неблокирующее чтение
        os.set_blocking(self.master, False)

    def _ingest(self, data: str) -> None:
        self.buf += data
        while "\n" in self.buf:
            line, self.buf = self.buf.split("\n", 1)
            line = line.rstrip("\r")
            self.all.append(line)
            self._note(line)

    def _note(self, line: str) -> None:
        if "<< Error" in line:
            self.errors.append(line)
        if "phase=FINISHED" in line or "FINISHED" in line and "GameState" in line:
            self.finished = True
        m = re.search(r"code=([A-Z0-9]{6})", line)
        if m:
            self.code = m.group(1)
        m = re.search(r"ok game=([a-f0-9-]+)", line)
        if m:
            self.game_id = m.group(1)
        m = re.search(r"account_id=([a-f0-9-]+)", line)
        if m:
            self.account_id = m.group(1)
        m = re.search(r"<< MatchStarted game=([a-f0-9-]+)", line)
        if m:
            self.game_id = m.group(1)

    def pump(self, timeout: float = 0.05) -> None:
        end = time.time() + timeout
        while time.time() < end:
            r, _, _ = select.select([self.master], [], [], max(0.0, end - time.time()))
            if not r:
                break
            try:
                data = os.read(self.master, 8192).decode("utf-8", "replace")
            except OSError as e:
                if e.errno in (errno.EAGAIN, errno.EWOULDBLOCK):
                    break
                raise
            if not data:
                break
            self._ingest(data)

    def wait_re(self, pat: str, timeout: float = 25.0) -> str:
        rx = re.compile(pat)
        deadline = time.time() + timeout
        while time.time() < deadline:
            self.pump(0.2)
            for line in reversed(self.all[-80:]):
                if rx.search(line):
                    return line
            # хвост без \n (prompt)
            if rx.search(self.buf):
                return self.buf
        raise TimeoutError(f"{self.name}: /{pat}/")

    def wait_menu(self, timeout: float = 20.0) -> None:
        self.wait_re(r"0\) выход", timeout=timeout)
        self.pump(0.15)

    def send(self, s: str) -> None:
        os.write(self.master, (s + "\n").encode())

    def recent_text(self, n: int = 120) -> str:
        return "\n".join(self.all[-n:] + ([self.buf] if self.buf else []))

    def quit(self) -> None:
        try:
            self.send("0")
        except Exception:
            pass
        try:
            self.proc.terminate()
        except Exception:
            pass
        try:
            os.close(self.master)
        except Exception:
            pass


def parse_card(token: str, idx: int) -> Card | None:
    m = re.match(r"^(10|[6-9]|Валет|Дама|Король|Туз)\s+([♠♥♦♣])$", token.strip())
    if not m:
        return None
    return Card(RANK[m.group(1)], SUIT[m.group(2)], token.strip(), idx)


def parse_snap(text: str) -> Snapshot:
    snap = Snapshot()
    mode = None
    for line in text.splitlines():
        if line.startswith("Козырь:"):
            raw = line.split(":", 1)[1].strip()
            if raw and raw != "(нет)":
                c = parse_card(raw, 0)
                if c:
                    snap.trump = c.suit
            continue
        if "Стол:" in line:
            mode = "table"
            snap.pairs = []
            continue
        if "Ваши карты:" in line:
            mode = "hand"
            snap.hand = []
            continue
        if line.startswith("[") and "can_" in line:
            snap.can_pass = "can_pass" in line
            snap.can_bito = "can_bito" in line
            snap.can_take = "can_take" in line
            mode = None
            continue
        m = re.search(r"phase=(\w+)", line)
        if m and "GameState" in line:
            snap.phase = m.group(1)
        if mode == "table":
            m = re.match(r"^\s*(\d+)\.\s+(.+?)\s+>\s+(.+)\s*$", line)
            if m:
                atk = parse_card(m.group(2), int(m.group(1)))
                defc = None if m.group(3).strip() == "?" else parse_card(m.group(3), int(m.group(1)))
                if atk:
                    snap.pairs.append(Pair(int(m.group(1)), atk, defc))
        if mode == "hand":
            m = re.match(r"^\s*(\d+)\.\s+(.+)\s*$", line)
            if m:
                c = parse_card(m.group(2), int(m.group(1)))
                if c:
                    snap.hand.append(c)
    return snap


def beats(defense: Card, attack: Card, trump: str | None) -> bool:
    if defense.suit == attack.suit and defense.rank > attack.rank:
        return True
    if trump and defense.suit == trump and attack.suit != trump:
        return True
    if trump and defense.suit == trump and attack.suit == trump and defense.rank > attack.rank:
        return True
    return False


def lowest_beating(hand: list[Card], attack: Card, trump: str | None) -> Card | None:
    best = None
    for c in hand:
        if not beats(c, attack, trump):
            continue
        if best is None:
            best = c
            continue
        a_tr, b_tr = (c.suit == trump), (best.suit == trump)
        if a_tr != b_tr:
            if not a_tr:
                best = c
        elif c.rank < best.rank:
            best = c
    return best


def table_ranks(pairs: list[Pair]) -> set[int]:
    r: set[int] = set()
    for p in pairs:
        r.add(p.attack.rank)
        if p.defense:
            r.add(p.defense.rank)
    return r


def learn_trump_from_log(game_id: str) -> str | None:
    """Козырь из первой атакующей/отбивающей пары в IN hand не узнать — смотрим масть
    козырной карты в engine log нет. Эвристика: из успешной отбивки другой мастью."""
    path = LOGS / f"game-{game_id}.log"
    if not path.exists():
        return None
    text = path.read_text(errors="replace")
    # card=♠6 формат; ищем defense другой масти в DOMAIN отбил рядом с IN
    # Проще: после нескольких отбивок — если defense suit != attack suit → trump
    for m in re.finditer(
        r"IN Session/PlayCard.*?card=([♠♥♦♣])(\w+).*?target|IN Session/PlayCard.*?card=([♠♥♦♣])",
        text,
    ):
        pass
    # Пары в IN: не логируются defense отдельно достаточно.
    # Вытащим из hand= и action после отбил — skip.
    # Используем: все successful defense of different suit from attack in sequential DOMAIN.
    # Fallback None — choose_move перебирает масти.
    return None


def refresh(c: HumanClient) -> Snapshot:
    before = len(c.all)
    c.send("13")
    deadline = time.time() + 5
    while time.time() < deadline:
        c.pump(0.15)
        text = c.recent_text(60)
        if "Ваши карты:" in text or "(нет GameState)" in text:
            break
    c.pump(0.2)
    # снимок только свежих строк
    chunk = "\n".join(c.all[before:] + ([c.buf] if c.buf else []))
    snap = parse_snap(chunk if "Ваши карты:" in chunk or "Стол:" in chunk else c.recent_text(80))
    for line in c.all[-20:]:
        m = re.search(r"<< GameState .* phase=(\w+)", line)
        if m:
            snap.phase = m.group(1)
            if m.group(1) == "FINISHED":
                c.finished = True
    return snap


def send_play_line(c: HumanClient, line: str) -> None:
    err_before = len(c.errors)
    c.send("8")
    # ждём приглашение хода
    try:
        c.wait_re(r"Ход \(рука", timeout=5)
    except TimeoutError:
        c.pump(0.3)
    c.send(line)
    c.moves.append(line)
    c.pump(0.5)
    # отмена если зависли в prompt — отправим p и выйдем в меню? 
    new = c.errors[err_before:]
    return


def send_simple(c: HumanClient, key: str) -> None:
    mapping = {"pass": "9", "bito": "10", "ready": "11"}
    c.send(mapping[key])
    c.moves.append(key)
    c.pump(0.45)


def choose(snap: Snapshot, trump: str | None, rng: random.Random) -> str | None:
    if snap.phase == "FINISHED":
        return None
    undef = next((p for p in snap.pairs if p.defense is None), None)

    if snap.can_bito:
        return "bito"

    if undef is not None and (snap.can_take or True):
        # пробуем отбить; если can_take и (нет битья или random take)
        candidates: list[Card] = []
        suits = [trump] if trump else [None, "S", "H", "D", "C"]
        for su in suits:
            b = lowest_beating(snap.hand, undef.attack, su)
            if b and b not in candidates:
                candidates.append(b)
        # также любая старшая той же масти
        for c in snap.hand:
            if c.suit == undef.attack.suit and c.rank > undef.attack.rank and c not in candidates:
                candidates.append(c)
        if snap.can_take and (not candidates or rng.random() < 0.2):
            return "pass"
        if candidates:
            return f"{candidates[0].idx}>{undef.idx}"
        if snap.can_take:
            return "pass"

    if snap.pairs:
        ranks = table_ranks(snap.pairs)
        for c in snap.hand:
            if c.rank in ranks:
                return str(c.idx)
        if snap.can_pass:
            return "pass"

    if not snap.pairs and snap.hand:
        hand = sorted(snap.hand, key=lambda c: ((c.suit == trump) if trump else False, c.rank))
        return str(hand[0].idx)

    if snap.can_pass:
        return "pass"
    return None


def setup(host: HumanClient, guests: list[HumanClient]) -> None:
    for c in [host, *guests]:
        c.wait_menu(timeout=60)
        c.send("1")
        c.wait_re(r"ok login", timeout=30)
        print(f"  login {c.name} account={c.account_id}")
        c.wait_menu()

    host.send("2")
    host.wait_re(r"ok game=.*code=", timeout=20)
    print(f"  create code={host.code} game={host.game_id}")
    host.wait_menu()

    for g in guests:
        g.send("3")
        g.wait_re(r"код комнаты", timeout=10)
        g.send(host.code)
        g.wait_re(r"ok game=", timeout=20)
        print(f"  join {g.name}")
        g.wait_menu()

    for c in [host, *guests]:
        c.send("6")
    time.sleep(1.0)
    for c in [host, *guests]:
        c.pump(0.5)
        c.wait_re(r"RoomState|GameState|MatchStarted", timeout=20)

    host.wait_menu()
    host.send("7")
    for c in [host, *guests]:
        c.wait_re(r"MatchStarted|phase=IN_PROGRESS", timeout=25)
    print(f"  MatchStarted game={host.game_id}")


def infer_trump(game_id: str) -> str | None:
    path = LOGS / f"game-{game_id}.log"
    if not path.exists():
        return None
    lines = path.read_text(errors="replace").splitlines()
    # Ищем PlayCard с target (отбивка): card=X и в DOMAIN отбил; сравнить с предыдущей атакой card=
    last_attack_suit = None
    for ln in lines:
        m = re.search(r"IN Session/PlayCard.*action=playCard card=([♠♥♦♣])", ln)
        if m and "target" not in ln:
            # атака/подкид без target в логе? check format
            last_attack_suit = SUIT.get(m.group(1), m.group(1))
        if "DOMAIN отбил" in ln:
            # предыдущий IN PlayCard — defense
            pass
    # Более надёжно: пары card= в подряд IN после атаки
    attack_suit = None
    for ln in lines:
        if "IN Session/PlayCard" in ln and "card=" in ln:
            m = re.search(r"card=([♠♥♦♣])(\S+)", ln)
            if not m:
                continue
            suit = SUIT[m.group(1)]
            # если это отбивка — в логе может быть hand= и то же
            # эвристика после DOMAIN походил — attack; после DOMAIN отбил — defense
        if "DOMAIN походил" in ln or "DOMAIN подкинул" in ln:
            # mark next play as attack already logged before DOMAIN
            pass
    # Парсим хронологически:
    pending_card_suit = None
    pending_kind = None
    trump = None
    for ln in lines:
        if "IN Session/PlayCard" in ln:
            m = re.search(r"card=([♠♥♦♣])", ln)
            if m:
                pending_card_suit = SUIT[m.group(1)]
        if "DOMAIN походил" in ln or "DOMAIN подкинул" in ln:
            pending_kind = "atk"
            attack_suit = pending_card_suit
        if "DOMAIN отбил" in ln:
            if attack_suit and pending_card_suit and pending_card_suit != attack_suit:
                trump = pending_card_suit
                break
            pending_kind = "def"
    return trump


def main() -> int:
    addr = os.environ.get("FOOL_ADDR", "127.0.0.1:8080")
    ts = int(time.time())
    names = [f"HumA{ts}", f"HumB{ts}", f"HumC{ts}"]
    rng = random.Random(7)
    print(f"=== 3× human mode @ {addr} ===")
    clients = [HumanClient(n, addr) for n in names]
    host, *guests = clients

    try:
        setup(host, guests)
    except Exception as e:
        print("FAIL setup:", e)
        for c in clients:
            print("---", c.name, "tail ---")
            print(c.recent_text(50))
            c.quit()
        return 1

    game_id = host.game_id
    trump = None
    deadline = time.time() + 360
    idle = 0
    prev_moves = 0

    while time.time() < deadline:
        if sum(1 for c in clients if c.finished) >= 2:
            time.sleep(0.5)
            break

        acted = False
        for c in clients:
            if c.finished:
                continue
            try:
                c.wait_menu(timeout=8)
            except TimeoutError:
                c.pump(0.3)
            snap = refresh(c)
            if snap.trump:
                trump = snap.trump
            if c.finished or snap.phase == "FINISHED":
                c.finished = True
                continue
            mv = choose(snap, trump or snap.trump, rng)
            if mv is None:
                continue
            err_n = len(c.errors)
            if mv in ("pass", "bito", "ready"):
                send_simple(c, mv)
            else:
                send_play_line(c, mv)
            # чужой ход — норма
            for e in c.errors[err_n:]:
                low = e.lower()
                if any(x in low for x in ("not your", "не ваш", "cannot", "invalid", "illegal", "only", "forbidden", "нельзя", "не ваш ход")):
                    continue
                if "Error" in e:
                    print(f"  ! {c.name}: {e}")
            acted = True

        total = sum(len(c.moves) for c in clients)
        if total == prev_moves:
            idle += 1
        else:
            idle = 0
            prev_moves = total
            print(f"  moves={total} finished={[c.name for c in clients if c.finished]}")
        if idle > 50:
            print("! idle stop")
            break
        if not acted:
            time.sleep(0.25)

    print("\n=== client summary ===")
    for c in clients:
        print(f"{c.name}: fin={c.finished} moves={len(c.moves)} errs={len(c.errors)} id={c.account_id}")
        print(f"  last moves: {c.moves[-15:]}")

    log = LOGS / f"game-{game_id}.log"
    print(f"\n=== log {log.name} ===")
    ok = True
    if not log.exists():
        print("MISSING LOG")
        ok = False
    else:
        text = log.read_text(errors="replace")
        stats = {
            "походил": text.count("походил"),
            "отбил": text.count("отбил"),
            "подкинул": text.count("подкинул"),
            "взял карты": text.count("взял карты"),
            "объявил бито": text.count("объявил бито"),
            "бито — стол": text.count("бито —"),
            "подтвердил бито": text.count("подтвердил бито"),
            "дурак account_id": len(re.findall(r"дурак account_id=[a-f0-9-]+", text)),
            "IN Bito": text.count("IN Session/Bito"),
            "action=take": text.count("action=take"),
            "action=confirm_bito": text.count("action=confirm_bito"),
            "timer_left_ms": text.count("timer_left_ms="),
            "loser=0x": len(re.findall(r"loser=0x", text)),
            "players": 3 if "игроков=3" in text else -1,
        }
        for k, v in stats.items():
            print(f"  {k}: {v}")
        print("  last DOMAIN:")
        for ln in [x for x in text.splitlines() if "DOMAIN" in x][-15:]:
            print("   ", ln)
        if stats["loser=0x"]:
            ok = False
        if not any(c.finished for c in clients) and stats["дурак account_id"] == 0:
            ok = False
        # логика 3 игроков: ожидаем confirm_bito или хотя бы bito+ходы
        if stats["походил"] < 1:
            ok = False
            print("  FAIL: нет атак")
        if stats["IN Bito"] == 0 and stats["взял карты"] == 0:
            print("  WARN: ни бито ни взял — странно для полной партии")
        if stats["дурак account_id"] == 0 and "ничья" not in text and any(c.finished for c in clients):
            print("  WARN: FINISHED без дурака/ничьи в DOMAIN")

    for c in clients:
        c.quit()
        try:
            c.proc.wait(timeout=4)
        except Exception:
            c.proc.kill()

    print("OVERALL", "OK" if ok else "FAIL")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())

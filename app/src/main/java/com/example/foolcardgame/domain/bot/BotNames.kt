package com.example.foolcardgame.domain.bot

import kotlin.random.Random

/**
 * Display names for offline bots: Hollywood stars (Russian) + Russian classics.
 */
object BotNames {
    val hollywoodStars: List<String> = listOf(
        "Брэд Питт",
        "Леонардо ДиКаприо",
        "Том Круз",
        "Том Хэнкс",
        "Роберт Дауни-младший",
        "Джонни Депп",
        "Уилл Смит",
        "Дензел Вашингтон",
        "Морган Фриман",
        "Киану Ривз",
        "Крис Хемсворт",
        "Крис Эванс",
        "Райан Рейнольдс",
        "Хью Джекман",
        "Мэтт Деймон",
        "Джордж Клуни",
        "Скарлетт Йоханссон",
        "Мерил Стрип",
        "Дженнифер Лоуренс",
        "Натали Портман",
        "Энн Хэтэуэй",
        "Эмма Стоун",
        "Марго Робби",
        "Анджелина Джоли",
        "Сандра Баллок",
        "Николь Кидман",
        "Шарлиз Терон",
        "Зендея",
        "Галь Гадот",
        "Виола Дэвис",
    )

    val russianClassics: List<String> = listOf(
        "Александр Пушкин",
        "Лев Толстой",
        "Фёдор Достоевский",
        "Антон Чехов",
        "Николай Гоголь",
        "Иван Тургенев",
        "Михаил Лермонтов",
        "Александр Островский",
        "Иван Гончаров",
        "Николай Некрасов",
        "Александр Блок",
        "Сергей Есенин",
        "Владимир Маяковский",
        "Анна Ахматова",
        "Марина Цветаева",
        "Борис Пастернак",
        "Михаил Булгаков",
        "Максим Горький",
        "Александр Солженицын",
        "Иван Крылов",
        "Афанасий Фет",
        "Фёдор Тютчев",
        "Александр Грибоедов",
        "Николай Карамзин",
        "Владимир Даль",
        "Константин Паустовский",
        "Исаак Бабель",
        "Андрей Платонов",
        "Василий Шукшин",
        "Иван Бунин",
    )

    val all: List<String> = hollywoodStars + russianClassics

    /**
     * Picks [count] distinct names. Uses [random] so a session seed can make names stable.
     */
    fun pick(count: Int, random: Random = Random.Default): List<String> {
        require(count >= 0) { "count must be non-negative" }
        require(count <= all.size) {
            "Need at most ${all.size} bot names, got $count"
        }
        return all.shuffled(random).take(count)
    }
}

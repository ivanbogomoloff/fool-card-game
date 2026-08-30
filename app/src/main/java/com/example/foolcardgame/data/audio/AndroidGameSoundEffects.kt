package com.example.foolcardgame.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.foolcardgame.R
import com.example.foolcardgame.data.repository.ProfileRepository
import com.example.foolcardgame.domain.audio.GameSoundEffects
import com.example.foolcardgame.domain.audio.GameSoundKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AndroidGameSoundEffects(
    context: Context,
    profileRepository: ProfileRepository,
    scope: CoroutineScope,
) : GameSoundEffects {

    @Volatile
    private var soundsEnabled: Boolean = true

    private val soundPool: SoundPool
    private val soundIds: Map<GameSoundKind, Int>

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
        soundIds = mapOf(
            GameSoundKind.CARD_PLAY to soundPool.load(context, R.raw.sfx_card_play, 1),
            GameSoundKind.BITO to soundPool.load(context, R.raw.sfx_bito, 1),
            GameSoundKind.TAKE to soundPool.load(context, R.raw.sfx_take, 1),
        )
        profileRepository.observeProfile()
            .onEach { profile -> soundsEnabled = profile.soundsEnabled }
            .launchIn(scope)
    }

    override fun play(kind: GameSoundKind) {
        if (!soundsEnabled) return
        soundIds[kind]?.let { id ->
            soundPool.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}

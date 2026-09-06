package com.example.foolcardgame

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Инструментальный тест, выполняется на Android-устройстве.
 *
 * См. [документацию по тестированию](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context тестируемого приложения.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.foolcardgame", appContext.packageName)
    }
}
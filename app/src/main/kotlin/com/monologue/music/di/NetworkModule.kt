/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.monologue.music.di

import android.content.Context
import com.monologue.music.utils.NetworkConnectivityObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConnectivityObserver(@ApplicationContext context: Context): NetworkConnectivityObserver {
        return NetworkConnectivityObserver(context)
    }

    @Provides
    @Singleton
    fun provideAudioBookBayClient(@ApplicationContext context: Context): com.monologue.music.api.audiobookbay.AudioBookBayClient {
        return com.monologue.music.api.audiobookbay.AudioBookBayClient(context = context)
    }
}

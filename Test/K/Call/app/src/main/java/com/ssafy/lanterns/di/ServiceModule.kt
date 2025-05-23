package com.ssafy.lanterns.di

import android.content.Context
import com.ssafy.lanterns.service.call.CallServiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 서비스 관련 매니저 등록
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    /**
     * 통화 서비스 관련 의존성 주입
     */
    @Provides
    @Singleton
    fun provideCallServiceManager(@ApplicationContext context: Context): CallServiceManager {
        return CallServiceManager(context)
    }
} 
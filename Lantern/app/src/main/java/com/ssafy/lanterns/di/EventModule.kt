package com.ssafy.lanterns.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmergencyEventTrigger

@Module
@InstallIn(SingletonComponent::class)
object EventModule {

    @EmergencyEventTrigger // 어노테이션 사용
    @Singleton
    @Provides
    fun provideEmergencyEventFlow(): MutableSharedFlow<Unit> {
        return MutableSharedFlow()
    }

    @EmergencyEventTrigger
    @Singleton
    @Provides
    fun provideSharedEmergencyEventFlow(
        @EmergencyEventTrigger mutableFlow: MutableSharedFlow<Unit> // 위에서 생성된 MutableSharedFlow 인스턴스 주입
    ): SharedFlow<Unit> {
        return mutableFlow // MutableSharedFlow는 SharedFlow의 하위 타입이므로 바로 반환 가능
    }
}
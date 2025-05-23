package com.ssafy.lanterns.di

import android.content.Context
import com.ssafy.lanterns.data.repository.MessageRepository
import com.ssafy.lanterns.data.repository.MessageRepositoryImpl
import com.ssafy.lanterns.data.repository.MessagesDao
import com.ssafy.lanterns.data.source.ble.mesh.MessageDatabaseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideMessageDatabaseManager(
        @ApplicationContext context: Context
    ): MessageDatabaseManager {
        return MessageDatabaseManager(context)
    }
    
    @Provides
    @Singleton
    fun provideMessageRepository(
        messageDatabaseManager: MessageDatabaseManager,
        messagesDao: MessagesDao
    ): MessageRepository {
        return MessageRepositoryImpl(messageDatabaseManager, messagesDao)
    }
} 
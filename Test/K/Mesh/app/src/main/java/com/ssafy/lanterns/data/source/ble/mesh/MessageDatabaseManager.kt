package com.ssafy.lanterns.data.source.ble.mesh

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * BLE 메쉬 네트워크의 메시지를 로컬 SQLite DB에 저장하고 관리하는 클래스
 */
class MessageDatabaseManager(context: Context) {
    private val TAG = "MessageDatabaseManager"
    private val dbHelper = MessageDatabaseHelper(context)
    
    /**
     * 메시지 저장
     * @param message 저장할 메시지
     * @return 저장 성공 여부
     */
    fun saveMessage(message: MeshMessage): Boolean {
        try {
            val db = dbHelper.writableDatabase
            
            val values = ContentValues().apply {
                put(MessageTable.COLUMN_SEQUENCE, message.sequenceNumber)
                put(MessageTable.COLUMN_SENDER, message.sender)
                put(MessageTable.COLUMN_SENDER_NICKNAME, message.senderNickname)
                put(MessageTable.COLUMN_RECIPIENT, message.target)
                put(MessageTable.COLUMN_CONTENT, message.content)
                put(MessageTable.COLUMN_TIMESTAMP, message.timestamp)
                put(MessageTable.COLUMN_TYPE, message.messageType.ordinal)
            }
            
            val newRowId = db.insert(MessageTable.TABLE_NAME, null, values)
            return newRowId != -1L
        } catch (e: Exception) {
            Log.e(TAG, "메시지 저장 실패", e)
            return false
        }
    }
    
    /**
     * 모든 메시지 로드
     * @param limit 로드할 메시지 수 제한 (기본값 100)
     * @return 로드된 메시지 목록
     */
    fun loadMessages(limit: Int = 100): List<MeshMessage> {
        val messages = mutableListOf<MeshMessage>()
        
        try {
            val db = dbHelper.readableDatabase
            
            val cursor = db.query(
                MessageTable.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                "${MessageTable.COLUMN_TIMESTAMP} DESC",
                limit.toString()
            )
            
            with(cursor) {
                while (moveToNext()) {
                    val sequenceNumber = getLong(getColumnIndexOrThrow(MessageTable.COLUMN_SEQUENCE))
                    val sender = getString(getColumnIndexOrThrow(MessageTable.COLUMN_SENDER))
                    val senderNickname = getString(getColumnIndexOrThrow(MessageTable.COLUMN_SENDER_NICKNAME))
                    val recipientIndex = getColumnIndexOrThrow(MessageTable.COLUMN_RECIPIENT)
                    val recipient = if (isNull(recipientIndex)) null else getString(recipientIndex)
                    val content = getString(getColumnIndexOrThrow(MessageTable.COLUMN_CONTENT))
                    val timestamp = getLong(getColumnIndexOrThrow(MessageTable.COLUMN_TIMESTAMP))
                    val typeOrdinal = getInt(getColumnIndexOrThrow(MessageTable.COLUMN_TYPE))
                    
                    val message = MeshMessage(
                        sequenceNumber = sequenceNumber.toInt(),
                        ttl = 0, // 저장된 메시지는 TTL 0으로 설정
                        timestamp = timestamp,
                        sender = sender,
                        senderNickname = senderNickname,
                        target = recipient,
                        content = content,
                        messageType = MessageType.values()[typeOrdinal]
                    )
                    
                    messages.add(message)
                }
            }
            
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "메시지 로드 실패", e)
        }
        
        return messages
    }
    
    /**
     * 특정 채팅 상대와의 메시지만 로드
     * @param partnerAddress 채팅 상대 주소
     * @param limit 로드할 메시지 수 제한 (기본값 100)
     * @return 로드된 메시지 목록
     */
    fun loadDirectMessages(partnerAddress: String, limit: Int = 100): List<MeshMessage> {
        val messages = mutableListOf<MeshMessage>()
        
        try {
            val db = dbHelper.readableDatabase
            
            // 1:1 채팅 메시지 쿼리 조건
            val selection = "(${MessageTable.COLUMN_SENDER} = ? AND ${MessageTable.COLUMN_RECIPIENT} = ?) OR " +
                           "(${MessageTable.COLUMN_SENDER} = ? AND ${MessageTable.COLUMN_RECIPIENT} = ?)"
            val selectionArgs = arrayOf(
                partnerAddress, "", // 상대방이 나에게 보낸 메시지
                "", partnerAddress  // 내가 상대방에게 보낸 메시지
            )
            
            val cursor = db.query(
                MessageTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                "${MessageTable.COLUMN_TIMESTAMP} DESC",
                limit.toString()
            )
            
            with(cursor) {
                while (moveToNext()) {
                    val sequenceNumber = getLong(getColumnIndexOrThrow(MessageTable.COLUMN_SEQUENCE))
                    val sender = getString(getColumnIndexOrThrow(MessageTable.COLUMN_SENDER))
                    val senderNickname = getString(getColumnIndexOrThrow(MessageTable.COLUMN_SENDER_NICKNAME))
                    val recipientIndex = getColumnIndexOrThrow(MessageTable.COLUMN_RECIPIENT)
                    val target = if (isNull(recipientIndex)) null else getString(recipientIndex)
                    val content = getString(getColumnIndexOrThrow(MessageTable.COLUMN_CONTENT))
                    val timestamp = getLong(getColumnIndexOrThrow(MessageTable.COLUMN_TIMESTAMP))
                    val typeOrdinal = getInt(getColumnIndexOrThrow(MessageTable.COLUMN_TYPE))
                    
                    val message = MeshMessage(
                        sequenceNumber = sequenceNumber.toInt(),
                        ttl = 0, // 저장된 메시지는 TTL 0으로 설정
                        timestamp = timestamp,
                        sender = sender,
                        senderNickname = senderNickname,
                        target = target,
                        content = content,
                        messageType = MessageType.values()[typeOrdinal]
                    )
                    
                    messages.add(message)
                }
            }
            
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "메시지 로드 실패", e)
        }
        
        return messages
    }
    
    /**
     * 메시지 중복 여부 확인
     * @param sender 발신자 주소
     * @param sequenceNumber 시퀀스 번호
     * @return 이미 저장된 메시지인지 여부
     */
    fun hasMessage(sender: String, sequenceNumber: Long): Boolean {
        try {
            val db = dbHelper.readableDatabase
            
            val selection = "${MessageTable.COLUMN_SENDER} = ? AND ${MessageTable.COLUMN_SEQUENCE} = ?"
            val selectionArgs = arrayOf(sender, sequenceNumber.toString())
            
            val cursor = db.query(
                MessageTable.TABLE_NAME,
                arrayOf(MessageTable.COLUMN_ID),
                selection,
                selectionArgs,
                null,
                null,
                null
            )
            
            val hasMessage = cursor.count > 0
            cursor.close()
            return hasMessage
        } catch (e: Exception) {
            Log.e(TAG, "메시지 조회 실패", e)
            return false
        }
    }
    
    /**
     * 오래된 메시지 정리
     * @param daysToKeep 보관할 일수 (기본값 7일)
     * @return 삭제된 메시지 수
     */
    fun cleanupOldMessages(daysToKeep: Int = 7): Int {
        try {
            val db = dbHelper.writableDatabase
            
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            val selection = "${MessageTable.COLUMN_TIMESTAMP} < ?"
            val selectionArgs = arrayOf(cutoffTime.toString())
            
            return db.delete(MessageTable.TABLE_NAME, selection, selectionArgs)
        } catch (e: Exception) {
            Log.e(TAG, "메시지 정리 실패", e)
            return 0
        }
    }
    
    /**
     * 데이터베이스 헬퍼 클래스
     */
    private class MessageDatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
            
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(MessageTable.SQL_CREATE_TABLE)
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 간단한 업그레이드 정책: 테이블 다시 생성
            db.execSQL(MessageTable.SQL_DELETE_TABLE)
            onCreate(db)
        }
        
        companion object {
            const val DATABASE_NAME = "mesh_messages.db"
            const val DATABASE_VERSION = 1
        }
    }
    
    /**
     * 메시지 테이블 상수
     */
    private object MessageTable {
        const val TABLE_NAME = "messages"
        const val COLUMN_ID = "id"
        const val COLUMN_SEQUENCE = "sequence_number"
        const val COLUMN_SENDER = "sender"
        const val COLUMN_SENDER_NICKNAME = "sender_nickname"
        const val COLUMN_RECIPIENT = "recipient"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_TYPE = "message_type"
        
        const val SQL_CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SEQUENCE INTEGER,
                $COLUMN_SENDER TEXT,
                $COLUMN_SENDER_NICKNAME TEXT,
                $COLUMN_RECIPIENT TEXT,
                $COLUMN_CONTENT TEXT,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_TYPE INTEGER
            )
        """
        
        const val SQL_DELETE_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
    }
} 
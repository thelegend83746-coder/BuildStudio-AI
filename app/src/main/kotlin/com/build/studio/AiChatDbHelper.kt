package com.build.studio

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class AiChatDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "build_studio_ai.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_CHAT = "chat_messages"
        const val COL_ID = "_id"
        const val COL_PROJECT_PATH = "project_path"
        const val COL_TYPE = "type" // 1 = user, 2 = assistant
        const val COL_TEXT = "text"
        const val COL_PLAN = "plan"
        const val COL_CONFIDENCE = "confidence"
        const val COL_ACTIONS_JSON = "actions_json"
        const val COL_TIMESTAMP = "timestamp"

        @Volatile
        private var instance: AiChatDbHelper? = null

        fun getInstance(context: Context): AiChatDbHelper {
            return instance ?: synchronized(this) {
                instance ?: AiChatDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_CHAT (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PROJECT_PATH TEXT NOT NULL,
                $COL_TYPE INTEGER NOT NULL,
                $COL_TEXT TEXT,
                $COL_PLAN TEXT,
                $COL_CONFIDENCE TEXT,
                $COL_ACTIONS_JSON TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_project ON $TABLE_CHAT ($COL_PROJECT_PATH)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHAT")
        onCreate(db)
    }

    fun saveMessage(
        projectPath: String,
        type: Int,
        text: String,
        plan: String? = null,
        confidence: String? = null,
        actions: List<BuildAiActivity.FileAction> = emptyList()
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PROJECT_PATH, projectPath)
            put(COL_TYPE, type)
            put(COL_TEXT, text)
            put(COL_PLAN, plan)
            put(COL_CONFIDENCE, confidence)

            if (actions.isNotEmpty()) {
                val arr = JSONArray()
                for (act in actions) {
                    val obj = JSONObject().apply {
                        put("type", act.type.name)
                        put("filePath", act.filePath)
                        put("code", act.code)
                        put("target", act.target)
                        put("replacement", act.replacement)
                        put("destPath", act.destPath)
                        put("applied", act.applied)
                    }
                    arr.put(obj)
                }
                put(COL_ACTIONS_JSON, arr.toString())
            } else {
                put(COL_ACTIONS_JSON, "")
            }
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        return db.insert(TABLE_CHAT, null, values)
    }

    fun loadMessages(projectPath: String): List<BuildAiActivity.ChatMessage> {
        val list = mutableListOf<BuildAiActivity.ChatMessage>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CHAT,
            null,
            "$COL_PROJECT_PATH = ?",
            arrayOf(projectPath),
            null,
            null,
            "$COL_TIMESTAMP ASC"
        )

        cursor.use { c ->
            val idxType = c.getColumnIndexOrThrow(COL_TYPE)
            val idxText = c.getColumnIndexOrThrow(COL_TEXT)
            val idxPlan = c.getColumnIndexOrThrow(COL_PLAN)
            val idxConfidence = c.getColumnIndexOrThrow(COL_CONFIDENCE)
            val idxActions = c.getColumnIndexOrThrow(COL_ACTIONS_JSON)

            while (c.moveToNext()) {
                val type = c.getInt(idxType)
                val text = c.getString(idxText) ?: ""
                val plan = c.getString(idxPlan)
                val confidence = c.getString(idxConfidence)
                val actionsJson = c.getString(idxActions) ?: ""

                val actionsList = mutableListOf<BuildAiActivity.FileAction>()
                if (actionsJson.isNotBlank()) {
                    try {
                        val arr = JSONArray(actionsJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val actionType = try {
                                BuildAiActivity.ActionType.valueOf(obj.getString("type"))
                            } catch (_: Exception) {
                                BuildAiActivity.ActionType.WRITE_FILE
                            }
                            actionsList.add(
                                BuildAiActivity.FileAction(
                                    type = actionType,
                                    filePath = obj.optString("filePath", ""),
                                    code = obj.optString("code", ""),
                                    target = obj.optString("target", ""),
                                    replacement = obj.optString("replacement", ""),
                                    destPath = obj.optString("destPath", ""),
                                    applied = obj.optBoolean("applied", false)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                list.add(
                    BuildAiActivity.ChatMessage(
                        type = type,
                        text = text,
                        plan = plan,
                        confidence = confidence,
                        actions = actionsList,
                        isStreaming = false
                    )
                )
            }
        }
        return list
    }

    fun clearMessages(projectPath: String) {
        val db = writableDatabase
        db.delete(TABLE_CHAT, "$COL_PROJECT_PATH = ?", arrayOf(projectPath))
    }
}

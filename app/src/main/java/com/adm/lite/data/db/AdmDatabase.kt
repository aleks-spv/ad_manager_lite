/*
 * Copyright 2026 Alexandr <aleks.spv@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adm.lite.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ServerEntity::class], version = 1, exportSchema = true)
abstract class AdmDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao

    companion object {
        fun build(context: Context): AdmDatabase =
            Room.databaseBuilder(context, AdmDatabase::class.java, "adm.db")
                // ponytail: при смене схемы добавить Migration/AutoMigration, схемы в app/schemas
                .build()
    }
}

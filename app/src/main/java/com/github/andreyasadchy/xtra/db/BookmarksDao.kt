package com.github.andreyasadchy.xtra.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.github.andreyasadchy.xtra.model.offline.Bookmark

@Dao
interface BookmarksDao {

    @Query("SELECT * FROM bookmarks")
    fun getAllLiveData(): LiveData<List<Bookmark>>

    @Query("SELECT * FROM bookmarks")
    fun getAll(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE videoId = :id")
    fun getByVideoId(id: String): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE userId = :id")
    fun getByUserId(id: String): List<Bookmark>

    @Insert
    fun insert(video: Bookmark)

    @Delete
    fun delete(video: Bookmark)

    @Update
    fun update(video: Bookmark)
}

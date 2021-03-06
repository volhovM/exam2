package me.volhovm.android.exam2

import java.io.{BufferedReader, InputStreamReader}

import android.content.{ContentResolver, Context}
import android.database.Cursor
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import android.provider.BaseColumns
import android.util.Log
import org.json.JSONArray

object DatabaseHelper extends BaseColumns {
  private val DATABASE_VERSION: Int = 1
  val DATABASE_NAME = "exam2.db"

  val FIRST_TABLE_NAME = "songs"

  val SONG_NAME = "song_name"
  val SONG_ARTIST = "song_artist"
  val SONG_URL = "song_url"
  val SONG_DURATION = "song_duration"
  val SONG_POPULARITY = "song_popularity"
  val SONG_GENRES = "song_genres"
  //divided by :
  val SONG_YEAR = "song_year"

  val CREATE_FIRST_TABLE = "create table " +
    FIRST_TABLE_NAME + " (" +
    BaseColumns._ID + " integer primary key autoincrement" +
    ", " + SONG_NAME + " text not null" +
    ", " + SONG_ARTIST + " text not null" +
    ", " + SONG_URL + " text not null" +
    ", " + SONG_DURATION + " integer" +
    ", " + SONG_POPULARITY + " integer" +
    ", " + SONG_GENRES + " text not null" +
    ", " + SONG_YEAR + " integer" +
    ");"

  val PLAYLISTS_TABLE_NAME = "playlists"

  val PLAYLIST_NAME = "playlists_name"
  val SECOND_S2 = "playlists_songs" // ints divided by :

  val CREATE_SECOND_TABLE = "create table " +
    PLAYLISTS_TABLE_NAME + " (" +
    BaseColumns._ID + " integer primary key autoincrement" +
    ", " + PLAYLIST_NAME + " text not null" +
    ", " + SECOND_S2 + " text not null" +
    ");"
}

class DatabaseHelper(context: Context) extends SQLiteOpenHelper(
  context,
  DatabaseHelper.DATABASE_NAME,
  null,
  DatabaseHelper.DATABASE_VERSION) with BaseColumns {

  val mWrapper = new HelperWrapper(context.getContentResolver)

 import me.volhovm.android.exam2.DatabaseHelper._

  override def onCreate(db: SQLiteDatabase): Unit = {
    db.execSQL(CREATE_FIRST_TABLE)
    db.execSQL(CREATE_SECOND_TABLE)
    new Thread(new Runnable {
      override def run(): Unit = {
        val initSongList: List[Song] = {
          val ins = new BufferedReader(new InputStreamReader(context.getResources.openRawResource(R.raw.music)))
          val jsonArray: JSONArray = new JSONArray(ins.readLine())
          (for (i <- 0 until jsonArray.length()) yield {
            val currentItem = jsonArray.getJSONObject(i)
            val fullname = currentItem.getString("name")
            val artist = fullname.split("::")(0).trim
            val name = fullname.split("::")(1).trim
            val fullduration = currentItem.getString("duration")
            val duration = Integer.parseInt(fullduration.split(":")(0).trim) * 60 +
              Integer.parseInt(fullduration.split(":")(1).trim)
            new Song(
              name, artist,
              currentItem.getString("url"),
              duration,
              currentItem.getInt("popularity"),
              (for (genre <- 0 until currentItem.getJSONArray("genres").length()) yield {
                currentItem.getJSONArray("genres").getString(genre)
              }).toList,
              currentItem.getInt("year"),
              i
            )
          }).toList
        }
        if (initSongList.nonEmpty) {
          Log.d(this.toString, "Loading from music.txt")
          mWrapper.putSongs(initSongList)
          mWrapper.putPlayList(new PlayList(PlayList.mainPlayList, initSongList))
        } else Log.w(this.toString, "empty initmusiclist")
      }
    }
    ).start()
  }

    override def onUpgrade(p1: SQLiteDatabase, p2: Int, p3: Int): Unit = throw new UnsupportedOperationException("CANNOT UPGRADE DB")
  }

  class HelperWrapper(mContentResolver: ContentResolver) {
    def putSong(song: Song) = mContentResolver.insert(MyContentProvider.MAIN_CONTENT_URI, song.getValues)
    def putSongs(songs: List[Song]) = songs.foreach(putSong)
    def putPlayList(playlist: PlayList) = mContentResolver.insert(MyContentProvider.AUXILIARY_CONTENT_URI, playlist.getValues)

    def getAllSongs: List[Song] =
      moveAndCompose(
        mContentResolver.query(MyContentProvider.MAIN_CONTENT_URI, null, null, null, null),
        Song.fromCursor
      )

    def getPlaylist(name: String): PlayList =
      moveAndCompose(
        mContentResolver.query(MyContentProvider.AUXILIARY_CONTENT_URI, null, DatabaseHelper.PLAYLIST_NAME + "='" + name + "'", null, null),
        PlayList.fromCursor(_, getAllSongs))(0)

    def getAllPlayLists: List[PlayList] =
      moveAndCompose(
        mContentResolver.query(MyContentProvider.AUXILIARY_CONTENT_URI, null, null, null, null),
        PlayList.fromCursor(_, getAllSongs))

    private def moveAndCompose[A](cursor: Cursor, foo: (Cursor) => A): List[A] = {
      cursor.moveToFirst()
      compose(cursor, foo)
    }

    private def compose[A](cursor: Cursor, foo: (Cursor) => A): List[A] =
      if (cursor.isAfterLast) {
        cursor.close(); Nil
      }
      else foo(cursor) :: compose({
        cursor.moveToNext()
        cursor
      }, foo)
  }
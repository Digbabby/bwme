package com.example.bwme;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DBNAME = "Login.db";

    public DatabaseHelper(Context context) {
        super(context, DBNAME, null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase MyDB) {
        MyDB.execSQL("create Table users(username TEXT primary key, password TEXT, email TEXT, displayName TEXT, profilePic TEXT)");
        MyDB.execSQL("create Table expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, amount REAL, category TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase MyDB, int oldVersion, int newVersion) {
        MyDB.execSQL("drop Table if exists users");
        MyDB.execSQL("drop Table if exists expenses");
        onCreate(MyDB);
    }

    public Boolean insertData(String username, String email, String password) {
        SQLiteDatabase MyDB = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("username", username);
        contentValues.put("email", email);
        contentValues.put("password", password);
        long result = MyDB.insert("users", null, contentValues);
        return result != -1;
    }

    public Boolean checkUsername(String username) {
        SQLiteDatabase MyDB = this.getReadableDatabase();
        android.database.Cursor cursor = MyDB.rawQuery("Select * from users where username = ?", new String[]{username});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public Boolean checkUsernamePassword(String username, String password) {
        SQLiteDatabase MyDB = this.getReadableDatabase();
        android.database.Cursor cursor = MyDB.rawQuery("Select * from users where username = ? and password = ?", new String[]{username, password});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public Boolean checkUserPassword(String username, String password) {
        SQLiteDatabase MyDB = this.getReadableDatabase();
        Cursor cursor = MyDB.rawQuery("Select * from users where username = ? and password = ?", new String[]{username, password});

        if (cursor.getCount() > 0) {
            cursor.close();
            return true;
        } else {
            cursor.close();
            return false;
        }
    }

    public Boolean insertExpense(String username, double amount, String category) {
        SQLiteDatabase DB = this.getWritableDatabase();
        ContentValues content = new ContentValues();

        content.put("username", username);
        content.put("amount", amount);
        content.put("category", category);

        long result = DB.insert("expenses", null, content);
        return result != -1;
    }

    public Cursor getExpensesByUser(String username) {
        SQLiteDatabase MyDB = this.getReadableDatabase();
        Cursor cursor = MyDB.rawQuery("Select * from expenses where username = ?", new String[]{username});
        return cursor;
    }

    public boolean updateProfile(String username, String name, String picPath) {
        SQLiteDatabase MyDB = this.getWritableDatabase();
        ContentValues content = new ContentValues();
        content.put("displayName", name);
        content.put("profilePic", picPath);
        long result = MyDB.update("users", content, "username = ?", new String[]{username});
        return result > 0;
    }

    public boolean isProfileNameSet(String username) {
        SQLiteDatabase MyDB = this.getReadableDatabase();
        Cursor cursor = MyDB.rawQuery("Select displayName from users where username = ?", new String[]{username});

        if (cursor.moveToFirst()) {
            String name = cursor.getString(0);
            cursor.close();
            return name != null && !name.isEmpty();
        }
        cursor.close();
        return false;
    }

}

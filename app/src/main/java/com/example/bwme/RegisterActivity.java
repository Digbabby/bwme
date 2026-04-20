package com.example.bwme;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {
    EditText email, username, password, confirmPassword;
    Button signup;
    DatabaseHelper DB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        email = findViewById(R.id.regEmailEt);
        username = findViewById(R.id.regUsernameEt);
        password = findViewById(R.id.regPasswordEt);
        confirmPassword = findViewById(R.id.regConfirmPasswordEt);
        signup = findViewById(R.id.registerBtn);
        DB = new DatabaseHelper(this);

        signup.setOnClickListener(view -> {
            String userEmail = email.getText().toString();
            String user = username.getText().toString();
            String pass = password.getText().toString();
            String confirm = confirmPassword.getText().toString();

            if (userEmail.equals("") || user.equals("") || pass.equals("") || confirm.equals("")) {
                Toast.makeText(RegisterActivity.this, "Please enter all fields", Toast.LENGTH_SHORT).show();
            } else {
                if (pass.equals(confirm)) {
                    if (!DB.checkUsername(user)) {
                        if (DB.insertData(user, userEmail, pass)) {
                            Toast.makeText(RegisterActivity.this, "Registered successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Registration failed", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this, "User already exists", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.backToLoginTv).setOnClickListener(v -> finish());
    }
}

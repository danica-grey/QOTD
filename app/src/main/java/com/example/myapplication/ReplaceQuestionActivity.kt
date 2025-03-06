package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth


class ReplaceQuestionActivity : AppCompatActivity() {
    private lateinit var backButton: Button
    private lateinit var submitButton: Button
    private lateinit var questionReplace: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_replace_question)

        backButton = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }


    }

}
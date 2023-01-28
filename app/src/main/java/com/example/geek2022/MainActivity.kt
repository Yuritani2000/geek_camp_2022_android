package com.example.geek2022

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addConnectVehicleFragment()
    }

    /**
     *  ConnectVehicleFragmentを追加するメソッド
     */
    private fun addConnectVehicleFragment(){
        val fragment = ConnectVehicleFragment()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(R.id.container, fragment)
        transaction.commit()
    }
}
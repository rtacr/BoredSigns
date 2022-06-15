package com.zacharee1.boredsigns.proxies

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zacharee1.boredsigns.ConfigActivity

class WeatherProxy : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, ConfigActivity::class.java)
        intent.putExtra(ConfigActivity.SB_TYPE, ConfigActivity.WEATHER)

        startActivity(intent)
        finish()
    }
}

package com.cybor97.soundmapsource;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends Activity implements View.OnClickListener {
    private Thread audioRecordHolderThread = null;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        final TextView listeningTV = findViewById(R.id.listeningTV);
        listeningTV.setOnClickListener(this);

        audioRecordHolderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SoundEngine.getInstance()
                            .init(null)
                            .startTransferAsServer("0.0.0.0", 12001);
                } catch (IOException e) {
                    listeningTV.setTextColor(Color.RED);
                    listeningTV.setText(e.getMessage());
                }
            }
        });
        audioRecordHolderThread.start();
    }

    @Override
    public void onClick(View v) {

    }
}

package com.example.ldcloud.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.example.ldcloud.R;

public class SettingsFragment extends Fragment {

    private static final String SHARED_PREFS_NAME = "LdCloudSettings";
    private static final String KEY_ACCESS_KEY = "accessKey";
    private static final String KEY_SECRET_KEY = "secretKey";
    private static final String KEY_ITEM_TITLE = "itemTitle";

    private EditText editTextAccessKey;
    private EditText editTextSecretKey;
    private EditText editTextItemTitle;
    private Button buttonSaveSettings;

    private SharedPreferences sharedPreferences;

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize SharedPreferences
        if (getActivity() != null) {
            sharedPreferences = getActivity().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI elements
        editTextAccessKey = view.findViewById(R.id.edit_text_access_key);
        editTextSecretKey = view.findViewById(R.id.edit_text_secret_key);
        editTextItemTitle = view.findViewById(R.id.edit_text_item_title);
        buttonSaveSettings = view.findViewById(R.id.button_save_settings);

        // Load saved settings
        loadSettings();

        // Set click listener for the save button
        buttonSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
    }

    private void loadSettings() {
        if (sharedPreferences != null) {
            String accessKey = sharedPreferences.getString(KEY_ACCESS_KEY, "");
            String secretKey = sharedPreferences.getString(KEY_SECRET_KEY, "");
            String itemTitle = sharedPreferences.getString(KEY_ITEM_TITLE, "");

            editTextAccessKey.setText(accessKey);
            editTextSecretKey.setText(secretKey);
            editTextItemTitle.setText(itemTitle);
        }
    }

    private void saveSettings() {
        if (sharedPreferences != null && getContext() != null) {
            String accessKey = editTextAccessKey.getText().toString().trim();
            String secretKey = editTextSecretKey.getText().toString().trim(); // In a real app, encrypt this!
            String itemTitle = editTextItemTitle.getText().toString().trim();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_ACCESS_KEY, accessKey);
            editor.putString(KEY_SECRET_KEY, secretKey);
            editor.putString(KEY_ITEM_TITLE, itemTitle);
            editor.apply();

            Toast.makeText(getContext(), "Settings saved", Toast.LENGTH_SHORT).show();
        } else {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error saving settings", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

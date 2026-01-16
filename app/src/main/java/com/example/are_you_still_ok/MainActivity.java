package com.example.are_you_still_ok;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.example.are_you_still_ok.network.ApiResponse;
import com.example.are_you_still_ok.network.CheckinResponse;
import com.example.are_you_still_ok.network.CreateUserRequest;
import com.example.are_you_still_ok.network.RetrofitClient;
import com.example.are_you_still_ok.network.UpdateUserRequest;
import com.example.are_you_still_ok.network.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private LinearLayout registerLayout;
    private LinearLayout mainLayout;

    private EditText etUsername;
    private EditText etPhone;
    private EditText etEmergencyPhone;
    private EditText etGoldenHours;
    private CheckBox cbRemindEnabled;
    private Button btnStart;

    private Button btnCheckin;
    private TextView tvStatus;
    private TextView tvLastCheckin;
    private Button btnEditProfile;

    private SharedPreferences prefs;
    private static final String PREF_NAME = "AreYouOKPrefs";
    private static final String KEY_USER_ID = "USER_ID";
    private static final String KEY_LANGUAGE = "LANGUAGE";

    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load Locale before setting content view
        loadLocale();
        
        setContentView(R.layout.activity_main);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Init Views
        registerLayout = findViewById(R.id.register_layout);
        mainLayout = findViewById(R.id.main_layout);
        etUsername = findViewById(R.id.et_username);
        etPhone = findViewById(R.id.et_phone);
        etEmergencyPhone = findViewById(R.id.et_emergency_phone);
        etGoldenHours = findViewById(R.id.et_golden_hours);
        cbRemindEnabled = findViewById(R.id.cb_remind_enabled);
        btnStart = findViewById(R.id.btn_start);
        btnCheckin = findViewById(R.id.btn_checkin);
        tvStatus = findViewById(R.id.tv_status);
        tvLastCheckin = findViewById(R.id.tv_last_checkin);
        btnEditProfile = findViewById(R.id.btn_edit_profile);

        // Init Prefs
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Check Login State
        long userId = prefs.getLong(KEY_USER_ID, -1);
        if (userId != -1) {
            fetchUserInfo(userId);
        } else {
            showRegisterUI();
        }

        // Listeners
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleRegistration();
            }
        });

        btnCheckin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCheckin();
            }
        });

        btnEditProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditDialog();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_language) {
            showLanguageDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadLocale() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String language = prefs.getString(KEY_LANGUAGE, "zh");
        setLocale(language);
    }

    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        DisplayMetrics dm = resources.getDisplayMetrics();
        config.setLocale(locale);
        resources.updateConfiguration(config, dm);
    }
    
    private void showLanguageDialog() {
        final String[] languages = {"English", "中文"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Language");
        builder.setItems(languages, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedLang = "en";
                if (which == 1) {
                    selectedLang = "zh";
                }
                
                // Save selection
                SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                editor.putString(KEY_LANGUAGE, selectedLang);
                editor.apply();
                
                // Restart Activity
                recreate();
            }
        });
        builder.show();
    }

    private void showRegisterUI() {
        registerLayout.setVisibility(View.VISIBLE);
        mainLayout.setVisibility(View.GONE);
    }

    private void showMainUI() {
        registerLayout.setVisibility(View.GONE);
        mainLayout.setVisibility(View.VISIBLE);

        updateCheckinStatus();
    }

    private void fetchUserInfo(long userId) {
        RetrofitClient.getInstance().getUser(userId).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                    currentUser = response.body().data;
                    showMainUI();
                } else {
                    // If fetching user fails, maybe clear prefs and show register? 
                    // Or just show error. For now, let's show error and stay on register (or empty main).
                    Toast.makeText(MainActivity.this, "Failed to fetch user info", Toast.LENGTH_SHORT).show();
                    showRegisterUI();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                // In a real app, we might show a "Retry" button. 
                // Here we just stay on whatever screen, but since we haven't shown MainUI yet, 
                // it might look empty. Let's show Register UI as fallback or a loading state.
                // For simplicity, let's keep it simple.
            }
        });
    }

    private void updateCheckinStatus() {
        if (currentUser == null) return;

        String lastCheckinTime = currentUser.lastCheckinTime;
        String today = getTodayDate();
        String lastCheckinDate = "";
        
        // Reset styles first
        tvLastCheckin.setTextColor(getResources().getColor(android.R.color.darker_gray));
        // btnCheckin.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // Green

        if (lastCheckinTime != null && lastCheckinTime.length() >= 10) {
            lastCheckinDate = lastCheckinTime.substring(0, 10);
            
            // Check for Overdue
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date checkinDate = sdf.parse(lastCheckinTime);
                Date now = new Date();
                
                long diff = now.getTime() - checkinDate.getTime();
                long goldenHoursInMillis = (long) (currentUser.goldenHours != null ? currentUser.goldenHours : 24) * 60 * 60 * 1000;
                
                if (diff > goldenHoursInMillis) {
                    // Overdue!
                    tvLastCheckin.setTextColor(0xFFFF5722); // Deep Orange
                    // btnCheckin.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF9800)); // Orange
                    // Force background resource to be re-applied or tinting might override it incorrectly
                    // Actually backgroundTint applies to the background drawable. 
                    // Since we used a custom drawable, setting backgroundTint will tint the WHOLE drawable.
                    // This is fine for the "Overdue" state to make it orange.
                    
                    btnCheckin.setText(getString(R.string.btn_im_still_ok));
                    tvStatus.setText(getString(R.string.status_overdue));
                    btnCheckin.setEnabled(true);
                    
                    tvLastCheckin.setText(getString(R.string.prefix_last_checkin) + lastCheckinTime);
                    return; // Exit early as we have set the overdue state
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        tvLastCheckin.setText(getString(R.string.prefix_last_checkin) + (lastCheckinTime == null ? getString(R.string.text_never) : lastCheckinTime));

        if (today.equals(lastCheckinDate)) {
            tvStatus.setText(getString(R.string.status_checked_in));
            btnCheckin.setEnabled(false);
            btnCheckin.setText(getString(R.string.btn_done));
            // When done, maybe grey out? 
            // Default backgroundTint is #4CAF50 (Green) from reset styles.
            // If disabled, system usually handles greying out, but we can be explicit if needed.
        } else {
            tvStatus.setText(getString(R.string.status_waiting));
            btnCheckin.setEnabled(true);
            btnCheckin.setText(getString(R.string.btn_im_ok));
        }
    }

    private void handleRegistration() {
        String username = etUsername.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String emergencyPhone = etEmergencyPhone.getText().toString().trim();
        String goldenHoursStr = etGoldenHours.getText().toString().trim();
        boolean remindEnabled = cbRemindEnabled.isChecked();

        // Validation
        if (username.isEmpty() || emergencyPhone.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_username_emergency_req), Toast.LENGTH_SHORT).show();
            return;
        }

        if (remindEnabled && phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_phone_req_remind), Toast.LENGTH_SHORT).show();
            return;
        }

        int goldenHours = 24;
        if (!goldenHoursStr.isEmpty()) {
            try {
                goldenHours = Integer.parseInt(goldenHoursStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.msg_invalid_golden_hours), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        CreateUserRequest request = new CreateUserRequest(username, emergencyPhone);
        request.phone = phone.isEmpty() ? null : phone;
        request.goldenHours = goldenHours;
        request.remindEnabled = remindEnabled;
        
        // Disable button to prevent double click
        btnStart.setEnabled(false);

        RetrofitClient.getInstance().createUser(request).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> response) {
                btnStart.setEnabled(true);
                if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                    User user = response.body().data;
                    saveUser(user);
                    showMainUI();
                    Toast.makeText(MainActivity.this, String.format(getString(R.string.msg_welcome), user.username), Toast.LENGTH_SHORT).show();
                } else {
                    String msg = getString(R.string.msg_reg_failed);
                    if (response.body() != null) msg += ": " + response.body().message;
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                btnStart.setEnabled(true);
                Toast.makeText(MainActivity.this, String.format(getString(R.string.msg_network_error), t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleCheckin() {
        long userId = prefs.getLong(KEY_USER_ID, -1);
        if (userId == -1) return;

        btnCheckin.setEnabled(false);

        RetrofitClient.getInstance().checkIn(userId).enqueue(new Callback<ApiResponse<CheckinResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<CheckinResponse>> call, Response<ApiResponse<CheckinResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                    String checkinTime = response.body().data.checkinTime;
                    // Update memory
                    if (currentUser != null) {
                        currentUser.lastCheckinTime = checkinTime;
                    }
                    updateCheckinStatus();
                    Toast.makeText(MainActivity.this, getString(R.string.msg_checkin_success), Toast.LENGTH_SHORT).show();
                } else {
                    btnCheckin.setEnabled(true);
                    String msg = getString(R.string.msg_checkin_failed);
                    if (response.body() != null) msg += ": " + response.body().message;
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<CheckinResponse>> call, Throwable t) {
                btnCheckin.setEnabled(true);
                Toast.makeText(MainActivity.this, String.format(getString(R.string.msg_network_error), t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditDialog() {
        if (currentUser == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_profile, null);
        
        final EditText etEditUsername = dialogView.findViewById(R.id.et_edit_username);
        final EditText etEditPhone = dialogView.findViewById(R.id.et_edit_phone);
        final EditText etEditEmergencyPhone = dialogView.findViewById(R.id.et_edit_emergency_phone);
        final EditText etEditGoldenHours = dialogView.findViewById(R.id.et_edit_golden_hours);
        final CheckBox cbEditRemindEnabled = dialogView.findViewById(R.id.cb_edit_remind_enabled);
        
        // Pre-fill from memory
        etEditUsername.setText(currentUser.username);
        etEditPhone.setText(currentUser.phone != null ? currentUser.phone : "");
        etEditEmergencyPhone.setText(currentUser.emergencyPhone);
        etEditGoldenHours.setText(String.valueOf(currentUser.goldenHours != null ? currentUser.goldenHours : 24));
        cbEditRemindEnabled.setChecked(currentUser.remindEnabled != null ? currentUser.remindEnabled : false);
        
        builder.setView(dialogView)
                .setPositiveButton(getString(R.string.btn_save), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newUsername = etEditUsername.getText().toString().trim();
                        String newPhone = etEditPhone.getText().toString().trim();
                        String newEmergencyPhone = etEditEmergencyPhone.getText().toString().trim();
                        String newGoldenHoursStr = etEditGoldenHours.getText().toString().trim();
                        boolean newRemindEnabled = cbEditRemindEnabled.isChecked();

                        // Validation
                        if (newUsername.isEmpty() || newEmergencyPhone.isEmpty()) {
                            Toast.makeText(MainActivity.this, getString(R.string.msg_username_emergency_req), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (newRemindEnabled && newPhone.isEmpty()) {
                            Toast.makeText(MainActivity.this, getString(R.string.msg_phone_req_remind), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int newGoldenHours = 24;
                        if (!newGoldenHoursStr.isEmpty()) {
                            try {
                                newGoldenHours = Integer.parseInt(newGoldenHoursStr);
                            } catch (NumberFormatException e) {
                                Toast.makeText(MainActivity.this, getString(R.string.msg_invalid_golden_hours), Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                        updateUser(newUsername, newPhone, newEmergencyPhone, newGoldenHours, newRemindEnabled);
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void updateUser(String username, String phone, String emergencyPhone, int goldenHours, boolean remindEnabled) {
        long userId = prefs.getLong(KEY_USER_ID, -1);
        if (userId == -1) return;

        UpdateUserRequest request = new UpdateUserRequest();
        request.username = username;
        request.phone = phone.isEmpty() ? null : phone;
        request.emergencyPhone = emergencyPhone;
        request.goldenHours = goldenHours;
        request.remindEnabled = remindEnabled;

        RetrofitClient.getInstance().updateUser(userId, request).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().code == 200) {
                    // Fetch latest user info immediately after update
                    fetchUserInfo(userId);
                    Toast.makeText(MainActivity.this, getString(R.string.msg_profile_updated), Toast.LENGTH_SHORT).show();
                } else {
                    String msg = getString(R.string.msg_update_failed);
                    if (response.body() != null) msg += ": " + response.body().message;
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                Toast.makeText(MainActivity.this, String.format(getString(R.string.msg_network_error), t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUser(User user) {
        // Update Memory
        this.currentUser = user;

        // Update Persistence (Only ID)
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_USER_ID, user.id);
        editor.apply();
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
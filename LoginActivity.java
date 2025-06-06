package com.yuja.driver;

import static com.yuja.driver.common.Utils.TERMS_OF_SERVICE_LINK;
import static com.yuja.driver.common.Utils.goToActivityWithParameters;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.yuja.driver.common.BaseActivity;
import com.yuja.driver.common.CustomProgressDialog;
import com.yuja.driver.common.CustomToast;

import org.greenrobot.eventbus.EventBus;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class LoginActivity extends BaseActivity {

    AppCompatImageView clearIv;
    MaterialTextView headingTv, descriptionTv, countryCodeTv, phoneNumberErrorTv, termsOfServiceTv;
    ImageView countryIconIv;
    TextInputLayout phoneTil;
    TextInputEditText phoneNumberEt;
    MaterialButton getOtpButton;
    MaterialCheckBox termsOfServiceCb;
    FirebaseAuth firebaseAuth;
    private Boolean showedError = false;
    private ActivityResultLauncher<IntentSenderRequest> phoneActivityResultLauncher;

    /**
     * This activity is used for entering phone number
     * when the activity is opened then it shows the hint of phone number
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        headingTv = findViewById(R.id.tv_heading);
        descriptionTv = findViewById(R.id.tv_description);
        countryCodeTv = findViewById(R.id.tv_country_code);
        phoneNumberErrorTv = findViewById(R.id.tv_phone_number_error);
        countryIconIv = findViewById(R.id.iv_country_icon);
        clearIv = findViewById(R.id.iv_clear);
        phoneTil = findViewById(R.id.til_phone);
        phoneNumberEt = findViewById(R.id.et_phone_number);
        termsOfServiceCb = findViewById(R.id.cb_terms_of_service);
        termsOfServiceTv = findViewById(R.id.tv_terms_of_service);
        getOtpButton = findViewById(R.id.button_get_otp);
        

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        firebaseAuth = FirebaseAuth.getInstance();

        phoneActivityResultLauncher = getPhoneActivityResultLauncher();

        GetPhoneNumberHintIntentRequest request = GetPhoneNumberHintIntentRequest.builder().build();

        Identity.getSignInClient(LoginActivity.this).getPhoneNumberHintIntent(request).addOnSuccessListener(pendingIntent -> {
            IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build();
            phoneActivityResultLauncher.launch(intentSenderRequest);
        });

        termsOfServiceTv.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("link", TERMS_OF_SERVICE_LINK);
            goToActivityWithParameters(LoginActivity.this, WebViewActivity.class, bundle, false);
        });

        phoneNumberEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().isEmpty()) {
                    clearIv.setVisibility(View.VISIBLE);
                } else {
                    clearIv.setVisibility(View.GONE);
                }
                if ((s.toString().length() != 10 && showedError) || (s.toString().length() == 10 && s == "1234567890")) {
                    phoneTil.setBackgroundResource(R.drawable.bg_edit_text_failure);
                } else {
                    phoneTil.setBackgroundResource(R.drawable.bg_edit_text_enabled);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 10) {
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(phoneNumberEt.getWindowToken(), 0);
                }
            }
        });

        clearIv.setOnClickListener(view -> phoneNumberEt.setText(""));

        getOtpButton.setOnClickListener(view -> {

            if (Objects.requireNonNull(phoneNumberEt.getText()).toString().length() != 10) {
                showedError = true;
                phoneTil.setBackgroundResource(R.drawable.bg_edit_text_failure);
                CustomToast.show(LoginActivity.this, getString(R.string.invalid_mobile_number), Toast.LENGTH_SHORT);
            } else if (!termsOfServiceCb.isChecked()) {
                CustomToast.show(LoginActivity.this, getString(R.string.please_read_and_accept_the_terms_of_service_to_continue), Toast.LENGTH_SHORT);
            } else {
                CustomProgressDialog.show(LoginActivity.this);
                String phoneNumber = "+91" + phoneNumberEt.getText().toString();
                sendVerificationCode(phoneNumber);
            }
        });
    }

    /**
     * When number entered and submit button pressed then the verification code send to the server
     *
     * @param phoneNumber of 10 digit phone number
     */
    public void sendVerificationCode(String phoneNumber) {
        PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                Log.d("TAG", "onVerificationCompleted: " + credential);
                runOnUiThread(() -> {
                    CustomProgressDialog.dismiss();
                });
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Log.e("TAG", "onVerificationFailed: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    CustomProgressDialog.dismiss();
                    CustomToast.show(LoginActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                });
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                Log.d("TAG", "onCodeSent: " + verificationId);
                runOnUiThread(() -> {
                    CustomProgressDialog.dismiss();
                    CustomToast.show(LoginActivity.this, getString(R.string.otp_sent_successfully_to, phoneNumber), Toast.LENGTH_SHORT);
                    Bundle bundle = new Bundle();
                    bundle.putString("phone", Objects.requireNonNull(phoneNumberEt.getText()).toString().trim());
                    bundle.putString("completePhoneNo", phoneNumber);
                    bundle.putString("verificationId", verificationId);
                    goToActivityWithParameters(LoginActivity.this, OtpVerifyActivity.class, bundle, true);
                    finish();
                });
            }
        };



        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
        Log.d("TAG", "sendVerificationCode: " + options);
    }

    public ActivityResultLauncher<IntentSenderRequest> getPhoneActivityResultLauncher() {
        phoneActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            try {
                String phoneNumber = Identity.getSignInClient(LoginActivity.this).getPhoneNumberFromIntent(result.getData());
                phoneNumberEt.setText(phoneNumber.substring(phoneNumber.length() - 10));
            } catch (ApiException ignored) {
            }
        });
        return phoneActivityResultLauncher;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }
}
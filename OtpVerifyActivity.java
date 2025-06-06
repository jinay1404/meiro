package com.yuja.driver;

import static com.yuja.driver.common.Utils.TAG;
import static com.yuja.driver.common.Utils.goToActivity;
import static com.yuja.driver.common.Utils.goToActivityWithFlags;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chaos.view.PinView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.yuja.driver.common.BaseActivity;
import com.yuja.driver.common.CustomProgressDialog;
import com.yuja.driver.common.CustomToast;
import com.yuja.driver.common.MyApplication;
import com.yuja.driver.common.Session;
import com.yuja.driver.model.api.ApiClient;
import com.yuja.driver.model.api.ApiInterface;
import com.yuja.driver.model.api.request.CreateDriverRequest;
import com.yuja.driver.model.api.request.CreateFCMTokenRequest;
import com.yuja.driver.model.api.response.CreateAccessTokenResponse;
import com.yuja.driver.model.api.response.CreateDriverResponse;
import com.yuja.driver.model.api.response.CreateRefreshTokenResponse;
import com.yuja.driver.model.api.response.LatestApiDetailsResponse;
import com.yuja.driver.model.api.response.ReferralDetailsResponse;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * This activity starts when user entered the phone number and pressed the next button
 * Previous activity send the verification code
 * In this activity, the code will verify when verify button pressed
 */
public class OtpVerifyActivity extends BaseActivity {

    PinView otpPinView;
    MaterialButton cancelButton, verifyOtpButton;
    MaterialTextView phoneNumberTv, otpErrorTv, otpCountdownResendTv;
    Boolean isResendActivated = false;
    String verificationId;
    private CountDownTimer countDownTimer;
    private FirebaseAuth firebaseAuth;
    private Boolean showedError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verify);

        phoneNumberTv = findViewById(R.id.tv_phone_number);
        otpCountdownResendTv = findViewById(R.id.tv_otp_countdown_resend);
        otpErrorTv = findViewById(R.id.tv_otp_error);
        otpPinView = findViewById(R.id.pin_view_otp);
        otpPinView.setAnimationEnable(true);
        cancelButton = findViewById(R.id.button_cancel);
        verifyOtpButton = findViewById(R.id.button_verify_otp);

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        firebaseAuth = FirebaseAuth.getInstance();

        String phoneNumber = getIntent().getStringExtra("phone");
        String completePhoneNumber = getIntent().getStringExtra("completePhoneNo");
        verificationId = getIntent().getStringExtra("verificationId");

        phoneNumberTv.setText(getString(R.string.enter_code_sent_to, completePhoneNumber));

        // Set the duration of the countdown in milliseconds
        long countdownDuration = 60000; // 60 seconds
        countDownTimer = new CountDownTimer(countdownDuration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Update the TextView with the remaining time
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                otpCountdownResendTv.setText(String.valueOf(secondsRemaining));
            }

            @Override
            public void onFinish() {
                otpCountdownResendTv.setText(R.string.resend_otp);
                isResendActivated = true;
            }
        };

        countDownTimer.start();

        otpPinView.requestFocus();

        otpPinView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().length() != 6 && showedError) {
                    otpPinView.setLineColor(ContextCompat.getColor(getApplicationContext(), R.color.red_800));
                    otpErrorTv.setVisibility(View.VISIBLE);
                } else if (s.toString().length() == 6) {
                    TypedValue value = new TypedValue();
                    OtpVerifyActivity.this.getTheme().resolveAttribute(R.attr.colorPrimaryAccent, value, true);
                    otpPinView.setLineColor(value.data);
                } else {
                    otpPinView.setLineColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_500));
                    otpErrorTv.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 6) {
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(otpPinView.getWindowToken(), 0);
                }
            }
        });

        cancelButton.setOnClickListener(v -> goToActivity(OtpVerifyActivity.this, LoginActivity.class, true));

        verifyOtpButton.setOnClickListener(view -> {

            CustomProgressDialog.show(OtpVerifyActivity.this);
            otpPinView.setLineColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_500));
            otpErrorTv.setVisibility(View.GONE);

            String otp = Objects.requireNonNull(otpPinView.getText()).toString().trim();

            try {
                PhoneAuthCredential phoneAuthCredential = PhoneAuthProvider.getCredential(verificationId, otp);
                firebaseAuth.signInWithCredential(phoneAuthCredential).addOnCompleteListener(OtpVerifyActivity.this, task1 -> {
                    if (task1.isSuccessful()) {

                        FirebaseUser firebaseUser = task1.getResult().getUser();

                        if (firebaseUser != null) {
                            assert phoneNumber != null;
                            Session.setMobile(Long.parseLong(phoneNumber));
                            Session.setFullMobile(firebaseUser.getPhoneNumber());
                            Session.setFirebaseDriverId(firebaseUser.getUid());

                            firebaseUser.getIdToken(true).addOnCompleteListener(task2 -> {
                                if (task2.isSuccessful()) {
                                    Session.setFirebaseIdToken(task2.getResult().getToken());

                                    Log.d(TAG, Objects.requireNonNull(task2.getResult().getToken()));

                                    MyApplication.getExecutorServiceThreadPool().submit(() -> {

                                        ApiInterface apiInterfaceBaseClient = ApiClient.getBaseClient().create(ApiInterface.class);
                                        Call<CreateRefreshTokenResponse> createRefreshTokenCall = apiInterfaceBaseClient.createRefreshToken(Session.getFirebaseIdToken());
                                        createRefreshTokenCall.enqueue(new Callback<>() {
                                            @Override
                                            public void onResponse(@NonNull Call<CreateRefreshTokenResponse> call1, @NonNull Response<CreateRefreshTokenResponse> response1) {
                                                if (response1.isSuccessful() && response1.body() != null) {

                                                    String refreshToken = response1.body().getRefreshToken();
                                                    Session.setRefreshToken(refreshToken);

                                                    Call<CreateAccessTokenResponse> createAccessTokenCall = apiInterfaceBaseClient.createAccessToken(firebaseUser.getUid(), "Bearer " + refreshToken);
                                                    createAccessTokenCall.enqueue(new Callback<>() {
                                                        @Override
                                                        public void onResponse(@NonNull Call<CreateAccessTokenResponse> call2, @NonNull Response<CreateAccessTokenResponse> response2) {
                                                            if (response2.isSuccessful() && response2.body() != null) {
                                                                String accessToken = response2.body().getAccessToken();
                                                                Session.setAccessToken(accessToken);

                                                                Call<LatestApiDetailsResponse> latestApiDetailsCall = apiInterfaceBaseClient.latestApiDetails("Bearer " + Session.getAccessToken());
                                                                latestApiDetailsCall.enqueue(new Callback<>() {
                                                                    @Override
                                                                    public void onResponse(@NonNull Call<LatestApiDetailsResponse> call3, @NonNull Response<LatestApiDetailsResponse> response3) {
                                                                        if (response3.isSuccessful() && response3.body() != null) {
                                                                            LatestApiDetailsResponse referCodeApplyResponse = response3.body();
                                                                            Session.setApiVersion(referCodeApplyResponse.getDetails().getVersion());
                                                                            Session.setEndpointRoot(referCodeApplyResponse.getDetails().getEndpointRoot());

                                                                            ApiInterface apiInterfaceAccessClient = ApiClient.getAccessClient().create(ApiInterface.class);
                                                                            CreateDriverRequest createDriverRequest = new CreateDriverRequest(Session.getFirebaseDriverId(), Session.getMobile(), Session.getCurrentLanguageCode(), System.currentTimeMillis());
                                                                            Call<CreateDriverResponse> getMongoIdCall = apiInterfaceAccessClient.postDriverDetails("Bearer " + Session.getAccessToken(), createDriverRequest);

                                                                            getMongoIdCall.enqueue(new Callback<>() {
                                                                                @Override
                                                                                public void onResponse(@NonNull Call<CreateDriverResponse> call4, @NonNull Response<CreateDriverResponse> response4) {
                                                                                    if (response4.isSuccessful() && response4.body() != null) {

                                                                                        Session.setMongoDriverId(response4.body().getMongoDriverId());
                                                                                        Session.setReferralCode(response4.body().getReferralCode());

                                                                                        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task3 -> {
                                                                                            if (task3.isSuccessful()) {
                                                                                                Session.setFCMToken(task3.getResult());

                                                                                                Call<ReferralDetailsResponse> getReferralDetailsCall = apiInterfaceAccessClient.getReferralDetails(Session.getMongoDriverId(), "Bearer " + Session.getAccessToken());

                                                                                                getReferralDetailsCall.enqueue(new Callback<>() {
                                                                                                    @Override
                                                                                                    public void onResponse(@NonNull Call<ReferralDetailsResponse> call5, @NonNull Response<ReferralDetailsResponse> response5) {
                                                                                                        if (response5.isSuccessful() && response5.body() != null) {

                                                                                                            ReferralDetailsResponse referralDetailsResponse = response5.body();

                                                                                                            List<ReferralDetailsResponse.ReferralResponse.ReferralsResponse.CouponsResponse> couponsResponseList = referralDetailsResponse.getReferral().getReferrals().getCoupons();

                                                                                                            DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference().child("driverStatistics").child(Session.getMongoDriverId());
                                                                                                            databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                                                                                                @Override
                                                                                                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                                                                                    if (snapshot.child("driverVerified").exists()) {
                                                                                                                        Boolean driverVerified = snapshot.child("driverVerified").getValue(Boolean.class);

                                                                                                                        if (Boolean.TRUE.equals(driverVerified)) {

                                                                                                                            runOnUiThread(CustomProgressDialog::dismiss);

                                                                                                                            Bundle bundle = new Bundle();
                                                                                                                            bundle.putInt("new_task", Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                                                                            bundle.putInt("clear_task", Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                                                                                            goToActivityWithFlags(OtpVerifyActivity.this, MainActivity.class, bundle, true);
                                                                                                                        } else {
                                                                                                                            @SuppressLint("HardwareIds") String androidId = Settings.Secure.getString(OtpVerifyActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);

                                                                                                                            CreateFCMTokenRequest createFCMTokenRequest = new CreateFCMTokenRequest(task3.getResult(), Session.getMongoDriverId(), androidId, System.currentTimeMillis(), Session.getCurrentLanguageCode(), Session.getCity() == null ? "" : Session.getCity());

                                                                                                                            Call<Void> postFCMTokenCall = apiInterfaceAccessClient.postFCMToken("Bearer " + Session.getAccessToken(), createFCMTokenRequest);

                                                                                                                            postFCMTokenCall.enqueue(new Callback<>() {
                                                                                                                                @Override
                                                                                                                                public void onResponse(@NonNull Call<Void> call6, @NonNull Response<Void> response6) {

                                                                                                                                    runOnUiThread(CustomProgressDialog::dismiss);

                                                                                                                                    Bundle bundle = new Bundle();
                                                                                                                                    bundle.putInt("new_task", Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                                                                                    bundle.putInt("clear_task", Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                                                                                                    goToActivityWithFlags(OtpVerifyActivity.this, MazeActivity.class, bundle, true);
                                                                                                                                }

                                                                                                                                @Override
                                                                                                                                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {

                                                                                                                                    runOnUiThread(() -> {
                                                                                                                                        CustomProgressDialog.dismiss();
                                                                                                                                        CustomToast.show(OtpVerifyActivity.this, getString(R.string.we_are_facing_some_issues_right_now), Toast.LENGTH_SHORT);
                                                                                                                                    });
                                                                                                                                }
                                                                                                                            });
                                                                                                                        }
                                                                                                                    } else {

                                                                                                                        @SuppressLint("HardwareIds") String androidId = Settings.Secure.getString(OtpVerifyActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);

                                                                                                                        CreateFCMTokenRequest createFCMTokenRequest = new CreateFCMTokenRequest(task3.getResult(), Session.getMongoDriverId(), androidId, System.currentTimeMillis(), Session.getCurrentLanguageCode(), Session.getCity() == null ? "" : Session.getCity());

                                                                                                                        Call<Void> postFCMTokenCall = apiInterfaceAccessClient.postFCMToken("Bearer " + Session.getAccessToken(), createFCMTokenRequest);

                                                                                                                        postFCMTokenCall.enqueue(new Callback<>() {
                                                                                                                            @Override
                                                                                                                            public void onResponse(@NonNull Call<Void> call6, @NonNull Response<Void> response6) {

                                                                                                                                if (!couponsResponseList.isEmpty()) {

                                                                                                                                    runOnUiThread(CustomProgressDialog::dismiss);
                                                                                                                                    Session.setIsDownloadReferralCodeApplied(true);

                                                                                                                                    Bundle bundle = new Bundle();
                                                                                                                                    bundle.putInt("new_task", Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                                                                                    bundle.putInt("clear_task", Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                                                                                                    goToActivityWithFlags(OtpVerifyActivity.this, PersonalInformationActivity.class, bundle, true);
                                                                                                                                } else {
                                                                                                                                    runOnUiThread(CustomProgressDialog::dismiss);

                                                                                                                                    Bundle bundle = new Bundle();
                                                                                                                                    bundle.putInt("new_task", Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                                                                                    bundle.putInt("clear_task", Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                                                                                                    goToActivityWithFlags(OtpVerifyActivity.this, ReferActivity.class, bundle, true);
                                                                                                                                }
                                                                                                                            }

                                                                                                                            @Override
                                                                                                                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                                                                                                                runOnUiThread(() -> {
                                                                                                                                    CustomProgressDialog.dismiss();
                                                                                                                                    CustomToast.show(OtpVerifyActivity.this, getString(R.string.we_are_facing_some_issues_right_now), Toast.LENGTH_SHORT);
                                                                                                                                });
                                                                                                                            }
                                                                                                                        });
                                                                                                                    }
                                                                                                                }

                                                                                                                @Override
                                                                                                                public void onCancelled(@NonNull DatabaseError error) {

                                                                                                                    runOnUiThread(() -> {
                                                                                                                        CustomProgressDialog.dismiss();
                                                                                                                        CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                                                                    });
                                                                                                                }
                                                                                                            });
                                                                                                        } else {
                                                                                                            runOnUiThread(() -> {
                                                                                                                CustomProgressDialog.dismiss();
                                                                                                                CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                                                            });
                                                                                                        }
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onFailure(@NonNull Call<ReferralDetailsResponse> call, @NonNull Throwable t) {
                                                                                                        runOnUiThread(() -> {
                                                                                                            CustomProgressDialog.dismiss();
                                                                                                            CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                                                        });
                                                                                                    }
                                                                                                });
                                                                                            }
                                                                                        });
                                                                                    } else {
                                                                                        runOnUiThread(() -> {
                                                                                            CustomProgressDialog.dismiss();
                                                                                            CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                                        });
                                                                                    }
                                                                                }

                                                                                @Override
                                                                                public void onFailure(@NonNull Call<CreateDriverResponse> call1, @NonNull Throwable t) {
                                                                                    runOnUiThread(() -> {
                                                                                        CustomProgressDialog.dismiss();
                                                                                        CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                                    });
                                                                                }
                                                                            });
                                                                        } else {
                                                                            runOnUiThread(() -> {
                                                                                CustomProgressDialog.dismiss();
                                                                                CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                            });
                                                                        }
                                                                    }

                                                                    @Override
                                                                    public void onFailure(@NonNull Call<LatestApiDetailsResponse> call, @NonNull Throwable t) {
                                                                        runOnUiThread(() -> {
                                                                            CustomProgressDialog.dismiss();
                                                                            CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                        });
                                                                    }
                                                                });
                                                            } else {
                                                                runOnUiThread(() -> {
                                                                    CustomProgressDialog.dismiss();
                                                                    CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                                });
                                                            }
                                                        }

                                                        @Override
                                                        public void onFailure(@NonNull Call<CreateAccessTokenResponse> call, @NonNull Throwable t) {
                                                            runOnUiThread(() -> {
                                                                CustomProgressDialog.dismiss();
                                                                CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                            });
                                                        }
                                                    });
                                                } else {
                                                    runOnUiThread(() -> {
                                                        CustomProgressDialog.dismiss();
                                                        CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                    });
                                                }
                                            }

                                            @Override
                                            public void onFailure(@NonNull Call<CreateRefreshTokenResponse> call, @NonNull Throwable t) {
                                                runOnUiThread(() -> {
                                                    CustomProgressDialog.dismiss();
                                                    CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
                                                });
                                            }
                                        });
                                    });
                                }
                            });
                        } else {
                            CustomProgressDialog.dismiss();
                            otpPinView.setText(null);
                        }
                    } else {
                        showedError = true;
                        CustomProgressDialog.dismiss();
                        otpErrorTv.setVisibility(View.VISIBLE);
                        otpPinView.setText(null);
                        otpPinView.setLineColor(ContextCompat.getColor(getApplicationContext(), R.color.red_800));
                    }
                });
            } catch (Exception e) {
                showedError = true;
                CustomProgressDialog.dismiss();
                otpErrorTv.setVisibility(View.VISIBLE);
                otpPinView.setText(null);
                otpPinView.setLineColor(ContextCompat.getColor(getApplicationContext(), R.color.red_800));
            }
        });

        otpCountdownResendTv.setOnClickListener(v -> {
            if (isResendActivated) {
                countDownTimer.start();
                isResendActivated = false;

                sendVerificationCode(completePhoneNumber);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }
    @Override
    public void onBackPressed() {
        firebaseAuth.signOut(); // Clear Firebase auth state
        Bundle bundle = new Bundle();
        bundle.putInt("new_task", Intent.FLAG_ACTIVITY_NEW_TASK);
        bundle.putInt("clear_task", Intent.FLAG_ACTIVITY_CLEAR_TASK);
        goToActivityWithFlags(OtpVerifyActivity.this, LoginActivity.class, bundle, true);
        finish(); // Finish OtpVerifyActivity
    }

    public void sendVerificationCode(String phoneNumber) {

        PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
//                signInWithPhoneAuthCredential(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                CustomToast.show(OtpVerifyActivity.this, getString(R.string.something_went_wrong_please_try_again), Toast.LENGTH_SHORT);
            }

            @Override
            public void onCodeSent(@NonNull String newVerificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                verificationId = newVerificationId;
                CustomToast.show(OtpVerifyActivity.this, getString(R.string.otp_resent_successfully_to, phoneNumber), Toast.LENGTH_SHORT);
            }
        };

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth).setPhoneNumber(phoneNumber).setTimeout(60L, TimeUnit.SECONDS).setActivity(this).setCallbacks(mCallbacks).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }
}
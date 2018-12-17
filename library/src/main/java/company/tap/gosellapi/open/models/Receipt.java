package company.tap.gosellapi.open.models;

import android.support.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public final class Receipt {

    @SerializedName("id")
    @Expose
    @Nullable private String id;

    @SerializedName("email")
    @Expose
    private boolean email;

    @SerializedName("sms")
    @Expose
    private boolean sms;

    public Receipt(boolean email, boolean sms) {
        this.email = email;
        this.sms = sms;
    }

    public String getId() {
        return id;
    }

    public boolean isEmail() {
        return email;
    }

    public boolean isSms() {
        return sms;
    }
}

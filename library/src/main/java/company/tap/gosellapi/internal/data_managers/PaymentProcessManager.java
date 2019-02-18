package company.tap.gosellapi.internal.data_managers;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

import company.tap.gosellapi.internal.Constants;
import company.tap.gosellapi.internal.api.callbacks.APIRequestCallback;
import company.tap.gosellapi.internal.api.callbacks.GoSellError;
import company.tap.gosellapi.internal.api.enums.AuthenticationType;
import company.tap.gosellapi.internal.api.enums.ExtraFeesStatus;
import company.tap.gosellapi.internal.api.enums.PaymentType;
import company.tap.gosellapi.internal.api.models.Card;
import company.tap.gosellapi.internal.api.models.CreateTokenSavedCard;
import company.tap.gosellapi.internal.api.models.SaveCard;
import company.tap.gosellapi.internal.api.models.SavedCard;
import company.tap.gosellapi.internal.api.requests.CreateOTPVerificationRequest;
import company.tap.gosellapi.internal.api.requests.CreateSaveCardRequest;
import company.tap.gosellapi.internal.api.requests.CreateTokenWithExistingCardDataRequest;
import company.tap.gosellapi.internal.data_managers.payment_options.PaymentOptionsDataManager;
import company.tap.gosellapi.internal.data_managers.payment_options.view_models.RecentSectionViewModel;
import company.tap.gosellapi.internal.data_managers.payment_options.view_models_data.CardCredentialsViewModelData;
import company.tap.gosellapi.internal.utils.Utils;
import company.tap.gosellapi.open.enums.TransactionMode;
import company.tap.gosellapi.internal.api.facade.GoSellAPI;
import company.tap.gosellapi.internal.api.models.AmountedCurrency;
import company.tap.gosellapi.internal.api.models.Authorize;
import company.tap.gosellapi.open.models.AuthorizeAction;
import company.tap.gosellapi.internal.api.models.Charge;
import company.tap.gosellapi.internal.api.models.CreateTokenCard;
import company.tap.gosellapi.open.models.Customer;
import company.tap.gosellapi.internal.api.models.ExtraFee;
import company.tap.gosellapi.internal.api.models.Order;
import company.tap.gosellapi.internal.api.models.PaymentOption;
import company.tap.gosellapi.open.models.Receipt;
import company.tap.gosellapi.open.models.Reference;
import company.tap.gosellapi.internal.api.models.SourceRequest;
import company.tap.gosellapi.internal.api.models.Token;
import company.tap.gosellapi.internal.api.models.TrackingURL;
import company.tap.gosellapi.internal.api.requests.CreateAuthorizeRequest;
import company.tap.gosellapi.internal.api.requests.CreateChargeRequest;
import company.tap.gosellapi.internal.api.requests.CreateTokenWithCardDataRequest;
import company.tap.gosellapi.internal.data_managers.payment_options.view_models.CardCredentialsViewModel;
import company.tap.gosellapi.internal.data_managers.payment_options.view_models.PaymentOptionViewModel;
import company.tap.gosellapi.internal.data_managers.payment_options.view_models.WebPaymentViewModel;
import company.tap.gosellapi.internal.interfaces.IPaymentDataProvider;
import company.tap.gosellapi.internal.interfaces.IPaymentProcessListener;
import company.tap.gosellapi.internal.utils.AmountCalculator;

final class PaymentProcessManager {

  PaymentDataManager.WebPaymentURLDecision decisionForWebPaymentURL(String url) {

    boolean urlIsReturnURL = url.startsWith(Constants.RETURN_URL);
    boolean shouldLoad = !urlIsReturnURL;
    boolean redirectionFinished = urlIsReturnURL;
    boolean shouldCloseWebPaymentScreen = false;

    if (getCurrentPaymentViewModel().getPaymentOption() instanceof PaymentOption)
      shouldCloseWebPaymentScreen = redirectionFinished && ((PaymentOption) getCurrentPaymentViewModel()
          .getPaymentOption()).getPaymentType() == PaymentType.CARD;

    if (getCurrentPaymentViewModel().getPaymentOption() instanceof CardCredentialsViewModelData)
      shouldCloseWebPaymentScreen = redirectionFinished;

    if(getCurrentPaymentViewModel().getPaymentOption() instanceof RecentSectionViewModel)
      shouldCloseWebPaymentScreen = redirectionFinished;

    System.out.println(" shouldOverrideUrlLoading : shouldCloseWebPaymentScreen :" + shouldCloseWebPaymentScreen);
    @Nullable String tapID = null;

    Uri uri = Uri.parse(url);
    if (uri.getQueryParameterNames().contains(
        Constants.TAP_ID)) {  // if ReturnURL contains TAP_ID which means web flow finished then get TAP_ID and stop reloading web view with any urls

      tapID = uri.getQueryParameter(Constants.TAP_ID);
    }

    return PaymentDataManager.getInstance().new WebPaymentURLDecision(shouldLoad,
        shouldCloseWebPaymentScreen, redirectionFinished, tapID);
  }


  public void checkSavedCardPaymentExtraFees(SavedCard savedCard,
                                             PaymentOptionsDataManager.PaymentOptionsDataListener paymentOptionsDataListener) {
    PaymentOption paymentOption = findSavedCardPaymentOption(savedCard);
    checkPaymentExtraFees(paymentOption, paymentOptionsDataListener,PaymentType.SavedCard);

  }

 public void  checkPaymentExtraFees(
                         @NonNull final PaymentOption paymentOption,
                         PaymentOptionsDataManager.PaymentOptionsDataListener paymentOptionsDataListener,
                         PaymentType paymentType) {
   BigDecimal feesAmount = calculateExtraFeesAmount(paymentOption);
   fireExtraFeesDecision(feesAmount, paymentOptionsDataListener, paymentType);
  }

  private void showExtraFees(AmountedCurrency amount,
                             AmountedCurrency extraFeesAmount,
                             PaymentOptionsDataManager.PaymentOptionsDataListener paymentOptionsDataListener,
                             PaymentType paymentType
                             ) {
    showExtraFeesAlert(amount, extraFeesAmount, new DialogManager.DialogResult() {
      @Override
      public void dialogClosed(boolean positiveButtonClicked) {
        if (positiveButtonClicked) {
          if(paymentType==PaymentType.WEB){
            paymentOptionsDataListener
                .fireWebPaymentExtraFeesUserDecision(ExtraFeesStatus.ACCEPT_EXTRA_FEES);
          }
        else if(paymentType==PaymentType.CARD){
            paymentOptionsDataListener
                .fireCardPaymentExtraFeesUserDecision(ExtraFeesStatus.ACCEPT_EXTRA_FEES);
          }
        else if(paymentType==PaymentType.SavedCard){
            paymentOptionsDataListener
                .fireSavedCardPaymentExtraFeesUserDecision(ExtraFeesStatus.ACCEPT_EXTRA_FEES);
          }
        } else {

          if(paymentType==PaymentType.WEB){
            paymentOptionsDataListener
                .fireWebPaymentExtraFeesUserDecision(ExtraFeesStatus.REFUSE_EXTRA_FEES);
          }
          else if(paymentType==PaymentType.CARD){
            paymentOptionsDataListener
                .fireCardPaymentExtraFeesUserDecision(ExtraFeesStatus.REFUSE_EXTRA_FEES);
          }
          else if(paymentType==PaymentType.SavedCard){
            paymentOptionsDataListener
                .fireSavedCardPaymentExtraFeesUserDecision(ExtraFeesStatus.REFUSE_EXTRA_FEES);
          }
        }
      }
    });
  }

  private PaymentOption findSavedCardPaymentOption(@NonNull SavedCard savedCard) {
    PaymentOption paymentOption = PaymentDataManager.getInstance().getPaymentOptionsDataManager()
        .findPaymentOption(savedCard.getPaymentOptionIdentifier());
    if (paymentOption != null)
      System.out.println("saved card payment name : " + paymentOption.getName());
    return paymentOption;
  }

  private void fireExtraFeesDecision(BigDecimal feesAmount,
                                     PaymentOptionsDataManager.PaymentOptionsDataListener paymentOptionsDataListener,
                                     PaymentType paymentType) {
    if (feesAmount.compareTo(BigDecimal.ZERO) == 1)
    {
      IPaymentDataProvider provider = getDataProvider();
      AmountedCurrency amount = provider.getSelectedCurrency();
      AmountedCurrency extraFeesAmount = new AmountedCurrency(amount.getCurrency(), feesAmount);
      showExtraFees(amount,extraFeesAmount,paymentOptionsDataListener,paymentType);
    }

    else
    {
      if (paymentType == PaymentType.WEB)
        paymentOptionsDataListener
            .fireWebPaymentExtraFeesUserDecision(ExtraFeesStatus.NO_EXTRA_FEES);
      else if (paymentType == PaymentType.CARD)
        paymentOptionsDataListener.fireCardPaymentExtraFeesUserDecision(ExtraFeesStatus.NO_EXTRA_FEES);
      else if (paymentType == PaymentType.SavedCard) {
        paymentOptionsDataListener.fireSavedCardPaymentExtraFeesUserDecision(ExtraFeesStatus.NO_EXTRA_FEES);
      }
    }

  }



  public BigDecimal calculateExtraFeesAmount(PaymentOption paymentOption) {
    if (paymentOption != null) {
      IPaymentDataProvider provider = getDataProvider();
      AmountedCurrency amount = provider.getSelectedCurrency();
      ArrayList<ExtraFee> extraFees = paymentOption.getExtraFees();
      if (extraFees == null)
        extraFees = new ArrayList<>();
      ArrayList<AmountedCurrency> supportedCurrencies = provider.getSupportedCurrencies();
      BigDecimal feesAmount = AmountCalculator
          .calculateExtraFeesAmount(extraFees, supportedCurrencies, amount);
      return feesAmount;
    } else
      return BigDecimal.ZERO;
  }

  public String calculateTotalAmount(BigDecimal feesAmount) {
    IPaymentDataProvider provider = getDataProvider();
    AmountedCurrency amount = provider.getSelectedCurrency();
    AmountedCurrency extraFeesAmount = new AmountedCurrency(amount.getCurrency(), feesAmount);
    AmountedCurrency totalAmount = new AmountedCurrency(amount.getCurrency(),
        amount.getAmount().add(extraFeesAmount.getAmount()), amount.getSymbol());
    String totalAmountText = Utils.getFormattedCurrency(totalAmount);
    return totalAmountText;
  }

  void startPaymentProcess(@NonNull final PaymentOptionViewModel paymentOptionModel) {
    forceStartPaymentProcess(paymentOptionModel);
  }

  void startSavedCardPaymentProcess(@NonNull final SavedCard paymentOptionModel,
                                    RecentSectionViewModel recentSectionViewModel){
    forceStartSavedCardPaymentProcess(paymentOptionModel,recentSectionViewModel);
  }

  PaymentProcessManager(@NonNull IPaymentDataProvider dataProvider,
                        @NonNull IPaymentProcessListener listener) {

    this.dataProvider = dataProvider;
    this.processListener = listener;
  }

  @NonNull
  IPaymentDataProvider getDataProvider() {

    return dataProvider;
  }

  @NonNull
  IPaymentProcessListener getProcessListener() {

    return processListener;
  }

  @Nullable private PaymentOptionViewModel currentPaymentViewModel;

  public void setCurrentPaymentViewModel(
      @Nullable PaymentOptionViewModel currentPaymentViewModel) {
    this.currentPaymentViewModel = currentPaymentViewModel;
  }


  @Nullable
  public PaymentOptionViewModel getCurrentPaymentViewModel() {
    return currentPaymentViewModel;
  }

  private IPaymentDataProvider dataProvider;
  private IPaymentProcessListener processListener;

  private void showExtraFeesAlert(AmountedCurrency amount, AmountedCurrency extraFeesAmount,
                                  DialogManager.DialogResult callback) {
    System.out.println(" showExtraFeesAlert .... ");
    AmountedCurrency totalAmount = new AmountedCurrency(amount.getCurrency(),
        amount.getAmount().add(extraFeesAmount.getAmount()), amount.getSymbol());

//    String extraFeesText = CurrencyFormatter.format(extraFeesAmount);
//    String totalAmountText = CurrencyFormatter.format(totalAmount);
    String extraFeesText = Utils.getFormattedCurrency(extraFeesAmount);
    String totalAmountText = Utils.getFormattedCurrency(totalAmount);

    String title = "Confirm extra charges";
    String message = String.format(
        "You will be charged an additional fee of %s for this type of payment, totaling an amount of %s",
        extraFeesText, totalAmountText);

    DialogManager.getInstance().showDialog(title, message, "Confirm", "Cancel", callback);
  }

  private void forceStartPaymentProcess(@NonNull PaymentOptionViewModel paymentOptionModel) {
    System.out.println(
        "paymentOptionModel instance of WebPaymentViewModel :" + (paymentOptionModel instanceof WebPaymentViewModel));
    System.out.println(
        "paymentOptionModel instance of CardCredentialsViewModel :" + (paymentOptionModel instanceof CardCredentialsViewModel));
    if (paymentOptionModel instanceof WebPaymentViewModel) {
      setCurrentPaymentViewModel(paymentOptionModel);
      startPaymentProcessWithWebPaymentModel((WebPaymentViewModel) paymentOptionModel);
    } else if (paymentOptionModel instanceof CardCredentialsViewModel) {
      setCurrentPaymentViewModel(paymentOptionModel);
      startPaymentProcessWithCardPaymentModel((CardCredentialsViewModel) paymentOptionModel);
    }
  }

  private void startPaymentProcessWithWebPaymentModel(
      @NonNull WebPaymentViewModel paymentOptionModel) {

    PaymentOption paymentOption = paymentOptionModel.getPaymentOption();
    System.out.println(
        "startPaymentProcessWithWebPaymentModel >>> paymentOption.getSourceId : " + paymentOption
            .getSourceId());
    SourceRequest source = new SourceRequest(paymentOption.getSourceId());

      callChargeOrAuthorizeOrSaveCardAPI(source, paymentOption, null, null);
  }


  private void startPaymentProcessWithCardPaymentModel(
      @NonNull CardCredentialsViewModel paymentOptionModel) {

    @Nullable CreateTokenCard card = paymentOptionModel.getCard();
    if (card == null) {
      return;
    }
    startPaymentProcessWithCard(card,
        paymentOptionModel.getSelectedCardPaymentOption(),
        paymentOptionModel.shouldSaveCard());
  }

  private void startPaymentProcessWithCard(@NonNull CreateTokenCard card,
                                           PaymentOption paymentOption, boolean saveCard) {

    CreateTokenWithCardDataRequest request = new CreateTokenWithCardDataRequest(card);

    callTokenAPI(request, paymentOption, saveCard);
  }

  private void callTokenAPI(@NonNull CreateTokenWithCardDataRequest request,
                            @NonNull final PaymentOption paymentOption,
                            @Nullable final boolean saveCard) {

    GoSellAPI.getInstance().createTokenWithEncryptedCard(request, new APIRequestCallback<Token>() {

      @Override
      public void onSuccess(int responseCode, Token serializedResponse) {

          System.out.println("startPaymentProcessWithCard >> serializedResponse: " + responseCode);
          System.out.println("startPaymentProcessWithCard >> transaction mode: " +
          PaymentDataManager.getInstance().getPaymentOptionsRequest().getTransactionMode());

        if(PaymentDataManager.getInstance().getPaymentOptionsRequest().getTransactionMode() == TransactionMode.SAVE_CARD
                || saveCard) {
            if(isCardSavedBefore(serializedResponse.getCard().getFingerprint())){
                fireCardSavedBeforeDialog();
                return;
            }
        }
            SourceRequest source = new SourceRequest(serializedResponse);
            callChargeOrAuthorizeOrSaveCardAPI(source, paymentOption, serializedResponse.getCard().getFirstSix(),
                    saveCard);
      }

      @Override
      public void onFailure(GoSellError errorDetails) {
        System.out.println("GoSellAPI.createToken : " + errorDetails.getErrorBody());
        closePaymentWithError(errorDetails);
      }
    });
  }


  private void fireCardSavedBeforeDialog(){
      String title = "Save Card";
      String message = "Your card has been saved before, you can not save it twice!";

      DialogManager.getInstance().showDialog(title, message, "OK", null, new DialogManager.DialogResult() {
          @Override
          public void dialogClosed(boolean positiveButtonClicked) {
              PaymentDataManager.getInstance().fireCardSavedBeforeListener();
          }
      });
  }

  private boolean isCardSavedBefore(@NonNull  String fingerprint){
      ArrayList<SavedCard> cards =  PaymentDataManager.getInstance().getPaymentOptionsDataManager().getPaymentOptionsResponse().getCards();
      System.out.println(" cards list check size :" +cards.size());
      if(cards == null || cards.size()==0) return  false;

      for(SavedCard card: cards){
          System.out.println(" cards list check fingerprint :"+ fingerprint +"  >>> savedcard finger:"+card.getFingerprint());
          if(card.getFingerprint().equals(fingerprint)) return true;
      }
      return  false;
  }

  /////////////////////////////////////////////////////////  Saved Card Payment process ////////////////////////////

  private void forceStartSavedCardPaymentProcess(@NonNull SavedCard savedCard,
                                                 RecentSectionViewModel recentSectionViewModel) {
    setCurrentPaymentViewModel(recentSectionViewModel);
    PaymentOption paymentOption =  findPaymentOption(savedCard);
    CreateTokenSavedCard createTokenSavedCard = new CreateTokenSavedCard(savedCard.getId(),dataProvider.getCustomer().getIdentifier());
    startPaymentProcessWithSavedCardPaymentModel(createTokenSavedCard,paymentOption);
  }



  private void startPaymentProcessWithSavedCardPaymentModel(
      @NonNull CreateTokenSavedCard createTokenSavedCard,PaymentOption paymentOption) {
    CreateTokenWithExistingCardDataRequest request = new  CreateTokenWithExistingCardDataRequest.Builder(createTokenSavedCard).build();
    callSavedCardTokenAPI(request, paymentOption, false);
  }


  private void callSavedCardTokenAPI(@NonNull CreateTokenWithExistingCardDataRequest request,
                            @NonNull final PaymentOption paymentOption,
                            @Nullable final boolean saveCard) {

    GoSellAPI.getInstance().createTokenWithExistingCard(request, new APIRequestCallback<Token>() {

      @Override
      public void onSuccess(int responseCode, Token serializedResponse) {
        System.out.println("startPaymentProcessWithSavedCard >> serializedResponse: " + serializedResponse);
        SourceRequest source = new SourceRequest(serializedResponse);
          callChargeOrAuthorizeOrSaveCardAPI(source, paymentOption, serializedResponse.getCard().getFirstSix(), saveCard);
      }

      @Override
      public void onFailure(GoSellError errorDetails) {
        System.out.println("GoSellAPI.callSavedCardTokenAPI : " + errorDetails.getErrorBody());
      }
    });
  }


  private void closePaymentWithError(GoSellError goSellError){
      handleChargeOrAuthorizeOrSaveCardResponse(null, goSellError);
  }
  private void callChargeOrAuthorizeOrSaveCardAPI(@NonNull SourceRequest source,
                                        @NonNull PaymentOption paymentOption,
                                        @Nullable String cardBIN, @Nullable Boolean saveCard) {

    Log.e("OkHttp", "CALL CHARGE API OR AUTHORIZE API");

    IPaymentDataProvider provider = getDataProvider();

    ArrayList<AmountedCurrency> supportedCurrencies = provider.getSupportedCurrencies();
    String orderID = provider.getPaymentOptionsOrderID();
    System.out.println("orderID : " + orderID);
    System.out.println("saveCard : " + saveCard);
    @Nullable String postURL = provider.getPostURL();
    System.out.println("postURL : " + postURL);
    @Nullable TrackingURL post = postURL == null ? null : new TrackingURL(postURL);

    AmountedCurrency amountedCurrency = provider.getSelectedCurrency();
    Customer customer = provider.getCustomer();
    BigDecimal fee = AmountCalculator
        .calculateExtraFeesAmount(paymentOption.getExtraFees(), supportedCurrencies,
            amountedCurrency);
    Order order = new Order(orderID);
    TrackingURL redirect = new TrackingURL(Constants.RETURN_URL);
    String paymentDescription = provider.getPaymentDescription();
    HashMap<String, String> paymentMetadata = provider.getPaymentMetadata();
    Reference reference = provider.getPaymentReference();
    boolean shouldSaveCard = saveCard == null ? false : saveCard;
    String statementDescriptor = provider.getPaymentStatementDescriptor();
    boolean require3DSecure = provider
        .getRequires3DSecure();// this.dataSource.getRequires3DSecure() || this.chargeRequires3DSecure();
    Receipt receipt = provider.getReceiptSettings();
    TransactionMode transactionMode = provider.getTransactionMode();
    System.out.println("transactionMode : " + transactionMode);
    switch (transactionMode) {

      case PURCHASE:

        CreateChargeRequest chargeRequest = new CreateChargeRequest(

            amountedCurrency.getAmount(),
            amountedCurrency.getCurrency(),
            customer,
            fee,
            order,
            redirect,
            post,
            source,
            paymentDescription,
            paymentMetadata,
            reference,
            shouldSaveCard,
            statementDescriptor,
            require3DSecure,
            receipt
        );

        GoSellAPI.getInstance().createCharge(chargeRequest, new APIRequestCallback<Charge>() {
          @Override
          public void onSuccess(int responseCode, Charge serializedResponse) {

              handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
          }

          @Override
          public void onFailure(GoSellError errorDetails) {

              handleChargeOrAuthorizeOrSaveCardResponse(null, errorDetails);
          }
        });

        break;

      case AUTHORIZE_CAPTURE:

        AuthorizeAction authorizeAction = provider.getAuthorizeAction();

        CreateAuthorizeRequest authorizeRequest = new CreateAuthorizeRequest(

            amountedCurrency.getAmount(),
            amountedCurrency.getCurrency(),
            customer,
            fee,
            order,
            redirect,
            post,
            source,
            paymentDescription,
            paymentMetadata,
            reference,
            shouldSaveCard,
            statementDescriptor,
            require3DSecure,
            receipt,
            authorizeAction
        );

        GoSellAPI.getInstance()
            .createAuthorize(authorizeRequest, new APIRequestCallback<Authorize>() {
              @Override
              public void onSuccess(int responseCode, Authorize serializedResponse) {
                  handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
              }

              @Override
              public void onFailure(GoSellError errorDetails) {

                  handleChargeOrAuthorizeOrSaveCardResponse(null, errorDetails);
              }
            });
        break;

        case SAVE_CARD:
            CreateSaveCardRequest saveCardRequest = new CreateSaveCardRequest(
                    amountedCurrency.getCurrency(),
                    customer,
                    order,
                    redirect,
                    post,
                    source,
                    paymentDescription,
                    paymentMetadata,
                    reference,
                    true,
                    statementDescriptor,
                    require3DSecure,
                    receipt,
                    true,
                    true,
                    true,
                    true,
                    true
            );

            GoSellAPI.getInstance().createSaveCard(saveCardRequest, new APIRequestCallback<SaveCard>() {
                @Override
                public void onSuccess(int responseCode, SaveCard serializedResponse) {

                    handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
                }

                @Override
                public void onFailure(GoSellError errorDetails) {

                    handleChargeOrAuthorizeOrSaveCardResponse(null, errorDetails);
                }
            });

            break;
    }
  }

  private void handleChargeOrAuthorizeOrSaveCardResponse(@Nullable Charge chargeOrAuthorizeOrSave,
                                               @Nullable GoSellError error) {

    if (chargeOrAuthorizeOrSave != null) {
        System.out.println("handleChargeOrAuthorizeResponse >>  chargeOrAuthorize : "+ chargeOrAuthorizeOrSave.getStatus());

      if (chargeOrAuthorizeOrSave instanceof Authorize) {
        getProcessListener().didReceiveAuthorize((Authorize) chargeOrAuthorizeOrSave);

      } else if(chargeOrAuthorizeOrSave instanceof SaveCard){
        getProcessListener().didReceiveSaveCard((SaveCard) chargeOrAuthorizeOrSave);

      }
      else
      {
          getProcessListener().didReceiveCharge(chargeOrAuthorizeOrSave);
      }
    } else {
        System.out.println("handleChargeOrAuthorizeResponse >>  error : "+error);
        getProcessListener().didReceiveError(error);
    }
  }

    /**
     * verify
     * @param chargeOrAuthorizeOrSaveCard
     * @param <T>
     */
  <T extends Charge> void retrieveChargeOrAuthorizeOrSaveCardAPI(T chargeOrAuthorizeOrSaveCard) {
    APIRequestCallback<T> callBack = new APIRequestCallback<T>() {
      @Override
      public void onSuccess(int responseCode, T serializedResponse) {
        System.out.println(" retrieveChargeOrAuthorizeOrSaveCardAPI >>> " + responseCode);
        if(serializedResponse!=null) System.out.println(" retrieveChargeOrAuthorizeOrSaveCardAPI >>> " + serializedResponse.getId());
       // System.out.println(" retrieveChargeOrAuthorizeOrSaveCardAPI >>> " + serializedResponse.getResponse().getMessage());
          handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
      }

      @Override
      public void onFailure(GoSellError errorDetails) {
          if(errorDetails!=null)
          System.out.println("retrieveChargeOrAuthorizeOrSaveCardAPI : onFailure >>> "+ errorDetails.getErrorBody());
      }
    };

    if (chargeOrAuthorizeOrSaveCard instanceof Authorize)
      GoSellAPI.getInstance()
          .retrieveAuthorize(chargeOrAuthorizeOrSaveCard.getId(), (APIRequestCallback<Authorize>) callBack);

    else if (chargeOrAuthorizeOrSaveCard  instanceof SaveCard) {
        GoSellAPI.getInstance()
                .retrieveSaveCard(chargeOrAuthorizeOrSaveCard.getId(), (APIRequestCallback<SaveCard>) callBack);
        System.out.println("#################### saveCardId 1 :"+ chargeOrAuthorizeOrSaveCard.getId());
    }
    else
        GoSellAPI.getInstance()
                .retrieveCharge(chargeOrAuthorizeOrSaveCard.getId(), (APIRequestCallback<Charge>) callBack);
  }


  public PaymentOption findPaymentOption(SavedCard savedCard) {
    return findSavedCardPaymentOption(savedCard);
  }

     void confirmChargeOTPCode(@NonNull Charge charge,String otpCode) {
    CreateOTPVerificationRequest createOTPVerificationRequest = new CreateOTPVerificationRequest.Builder(AuthenticationType.OTP,otpCode).build();
    APIRequestCallback<Charge> callBack = new APIRequestCallback<Charge>() {
      @Override
      public void onSuccess(int responseCode, Charge serializedResponse) {
        System.out.println(" confirmChargeOTPCode >>> " + serializedResponse.getResponse().getMessage());
          handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
      }

      @Override
      public void onFailure(GoSellError errorDetails) {
        System.out.println(" confirmChargeOTPCode >>> error : "+ errorDetails.getErrorBody());
          handleChargeOrAuthorizeOrSaveCardResponse(null,errorDetails);
      }
    };

      GoSellAPI.getInstance().authenticate_charge_transaction(charge.getId(),createOTPVerificationRequest,callBack);
  }


  void confirmAuthorizeOTPCode(@NonNull Authorize authorize, String otpCode) {
    CreateOTPVerificationRequest createOTPVerificationRequest = new CreateOTPVerificationRequest.Builder(AuthenticationType.OTP,otpCode).build();
    APIRequestCallback<Authorize> callBack = new APIRequestCallback<Authorize>() {
      @Override
      public void onSuccess(int responseCode, Authorize serializedResponse) {
        System.out.println(" confirmAuthorizeOTPCode >>> " + serializedResponse.getResponse().getMessage());
          handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
      }

      @Override
      public void onFailure(GoSellError errorDetails) {
        System.out.println(" confirmAuthorizeOTPCode >>> error : "+ errorDetails.getErrorBody());
          handleChargeOrAuthorizeOrSaveCardResponse(null,errorDetails);
      }
    };

      GoSellAPI.getInstance()
          .authenticate_authorize_transaction(authorize.getId(),createOTPVerificationRequest,callBack);

  }


  <T extends Charge> void resendChargeOTPCode(@NonNull Charge charge) {


    APIRequestCallback<Charge> callBack = new APIRequestCallback<Charge>() {
      @Override
      public void onSuccess(int responseCode, Charge serializedResponse) {
        System.out.println(" resendChargeOTPCode >>> inside call back type "+serializedResponse.getClass());
        System.out.println(" resendChargeOTPCode >>> " + serializedResponse.getResponse().getMessage());
        System.out.println(" resendChargeOTPCode >>> " + serializedResponse.getAuthenticate().getValue());
          handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
      }

      @Override
      public void onFailure(GoSellError errorDetails) {
        System.out.println(" resendChargeOTPCode >>> error : "+ errorDetails.getErrorBody());
          handleChargeOrAuthorizeOrSaveCardResponse(null,errorDetails);
      }
    };

    System.out.println(" resendChargeOTPCode >>> before call back type " + (charge.getClass()));

      GoSellAPI.getInstance()
          .request_authenticate_for_charge_transaction(charge.getId(),callBack);

  }


     void resendAuthorizeOTPCode(@NonNull Authorize authorize) {


      APIRequestCallback<Authorize> callBack = new APIRequestCallback<Authorize>() {
        @Override
        public void onSuccess(int responseCode, Authorize serializedResponse) {
          System.out.println(" resendAuthorizeOTPCode >>> inside call back type "+serializedResponse.getClass());
          System.out.println(" resendAuthorizeOTPCode >>> " + serializedResponse.getResponse().getMessage());
          System.out.println(" resendAuthorizeOTPCode >>> " + serializedResponse.getAuthenticate().getValue());
            handleChargeOrAuthorizeOrSaveCardResponse(serializedResponse, null);
        }

        @Override
        public void onFailure(GoSellError errorDetails) {
          System.out.println(" resendAuthorizeOTPCode >>> error : "+ errorDetails.getErrorBody());
            handleChargeOrAuthorizeOrSaveCardResponse(null,errorDetails);
        }
      };
    System.out.println(" resendAuthorizeOTPCode >>> before call back type " + (authorize.getClass()));

        GoSellAPI.getInstance()
            .request_authenticate_for_authorize_transaction(authorize.getId(),callBack);


  }
}

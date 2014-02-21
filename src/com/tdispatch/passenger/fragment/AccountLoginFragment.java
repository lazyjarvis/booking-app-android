package com.tdispatch.passenger.fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.tdispatch.passenger.R;
import com.tdispatch.passenger.api.ApiHelper;
import com.tdispatch.passenger.api.ApiRequest;
import com.tdispatch.passenger.api.ApiResponse;
import com.tdispatch.passenger.core.TDApplication;
import com.tdispatch.passenger.core.TDFragment;
import com.tdispatch.passenger.define.ErrorCode;
import com.tdispatch.passenger.fragment.dialog.GenericDialogFragment;
import com.tdispatch.passenger.iface.host.OAuthHostInterface;
import com.tdispatch.passenger.model.AccountData;
import com.tdispatch.passenger.model.CardData;
import com.tdispatch.passenger.model.OfficeData;
import com.tdispatch.passenger.model.VehicleData;
import com.tdispatch.passenger.tools.Office;
import com.webnetmobile.tools.JsonTools;
import com.webnetmobile.tools.WebnetLog;
import com.webnetmobile.tools.WebnetTools;

/*
 *********************************************************************************
 *
 * Copyright (C) 2013-2014 T Dispatch Ltd
 *
 * See the LICENSE for terms and conditions of use, modification and distribution
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *********************************************************************************
 *
 * @author Marcin Orlowski <marcin.orlowski@webnet.pl>
 *
 *********************************************************************************
*/

public class AccountLoginFragment extends TDFragment
{
	protected Handler mHandler = new Handler();
	protected WebView mWebView;

	protected String mOAuthRedirectUrl = Office.getApiUrl() + "/passenger/oauth/dummy/redirect";
	protected String mOAuthRedirectToGetTokensUrl = Office.getApiUrl() + "/passenger/oauth/dummy/redirect-to-get-tokens";

	@Override
	protected int getLayoutId() {
		return R.layout.oauth_fragment;
	}

	protected OAuthHostInterface mHostActivity;
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			mHostActivity = (OAuthHostInterface)activity;
		} catch( ClassCastException e ) {
			throw new ClassCastException("Host Activity needs to implement OAuthHostInterface");
		}
	}

	@SuppressWarnings( "deprecation" )
	@SuppressLint( "SetJavaScriptEnabled" )
	@Override
	protected void onPostCreateView() {

		ProgressBar pb = (ProgressBar)mFragmentView.findViewById(R.id.progressbar);
		pb.setVisibility( View.GONE );

		mWebView = (WebView)mFragmentView.findViewById( R.id.webview );
		if( mWebView != null ) {

			mWebView.setWebViewClient( new MyWebViewClient() );
			mWebView.setWebChromeClient( new MyWebchromeClient() );

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
				mWebView.setOnTouchListener( mOnTouchListener );
			}

			WebSettings webSettings = mWebView.getSettings();
			webSettings.setJavaScriptEnabled(true);
			webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
			webSettings.setAppCacheEnabled(false);
			webSettings.setSavePassword(false);
			webSettings.setSaveFormData(false);

			CookieSyncManager.createInstance( mContext );
			CookieManager cm = CookieManager.getInstance();
			cm.setAcceptCookie(true);
			cm.removeAllCookie();

			try {
				ApiRequest req = new ApiRequest( Office.getApiUrl() + "/passenger/oauth2/auth" );
				req.addGetParam("key", Office.getFleetApiKey());
				req.addGetParam("scope", "");
				req.addGetParam("response_type", "code");
				req.addGetParam("client_id", Office.getOAuthClientId());
				req.addGetParam("redirect_uri", mOAuthRedirectUrl);
				req.buildRequest();

				String url = req.getUrl();

				mWebView.loadUrl( url );

			} catch (Exception e ) {
				WebnetLog.e("Failed to load oauth launch page...");
			}

		} else {
			WebnetLog.e("Failed to init WebView. Aborting");
			mHostActivity.oAuthCancelled();
		}
	}


	private class MyWebViewClient extends WebViewClient {

		@TargetApi(Build.VERSION_CODES.FROYO)
		@Override
		public void onReceivedSslError( WebView webView, SslErrorHandler handler, SslError error ) {
			handler.proceed();
		}

		@Override
		public void onReceivedError(WebView webView, int errorCode, String description, String failingUrl ) {
			webView.loadUrl("file:///android_asset/connect_error.html");	// FIXME replace this HTML with native view
		};


		@Override
		public boolean shouldOverrideUrlLoading(WebView webView, String url) {

			Boolean consumed = false;

			if( url.startsWith( mOAuthRedirectUrl ) ) {
				Uri uri = Uri.parse( url );
				String mTemporaryAuthCode = uri.getQueryParameter("code");

				if( mTemporaryAuthCode != null) {
					if (mTemporaryAuthCode.equals("denied") == false) {
						WebnetTools.executeAsyncTask( new GetOAuthAccessTokenAsyncTask(), mTemporaryAuthCode );
					} else {
						mHostActivity.oAuthCancelled();
					}
				}


//				if( (mTemporaryAuthCode != null) && (mTemporaryAuthCode.equals("denied") == false) ) {
//					WebnetTools.executeAsyncTask( new GetOAuthAccessTokenAsyncTask(), mTemporaryAuthCode );
//
//				} else if( url.startsWith( mOAuthRedirectToGetTokensUrl ) ) {
//					// nothing
//				}

				consumed = true;
			}

			return consumed;
		};
	}

	private class MyWebchromeClient extends WebChromeClient {

		@Override
		public void onProgressChanged( WebView webView, int progress ) {

			ProgressBar pb = (ProgressBar)mFragmentView.findViewById(R.id.progressbar);
			if( progress > 0 ) {
				pb.setVisibility(View.VISIBLE);
			}

			pb.setProgress(progress);

			if( progress >= 100 ) {
				pb.setVisibility(View.GONE);
			}
		}
	}

	protected View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_UP:
					if (!v.hasFocus()) {
						v.requestFocus();
					}
					break;
			}
			return false;
		}
	};

	public class GetOAuthAccessTokenAsyncTask extends AsyncTask<String, Void, ApiResponse> {

		@Override
		protected void onPreExecute() {
			lockUI(true);
		}

		@Override
		protected ApiResponse doInBackground( String ... args ) {

			ApiResponse result = new ApiResponse();

			String tmpAccessCode = args[0];
			ApiResponse tokensApiResponse = new ApiResponse();

			ApiHelper api = ApiHelper.getInstance( mApp );
			try {
				tokensApiResponse = api.getOAuthTokens( tmpAccessCode );

				if( tokensApiResponse.getErrorCode() == ErrorCode.OK ) {

					TDApplication.getSessionManager().setAccessToken( JsonTools.getString(tokensApiResponse.getJSONObject(), "access_token") );
					TDApplication.getSessionManager().setRefreshToken( JsonTools.getString( tokensApiResponse.getJSONObject(), "refresh_token") );

					long expiresIn = JsonTools.getInt(tokensApiResponse.getJSONObject(), "expires_in", 0) * WebnetTools.MILLIS_PER_SECOND;
					expiresIn += System.currentTimeMillis();
					TDApplication.getSessionManager().setAccessTokenExpirationMillis( expiresIn + System.currentTimeMillis() );

					int errorCnt = 0;

					if( errorCnt == 0 ) {
						ApiResponse profileResponse = api.getAccountProfile();
						if( profileResponse.getErrorCode() == ErrorCode.OK ) {
							JSONObject tmp = profileResponse.getJSONObject();
							TDApplication.getSessionManager().putAccountData( new AccountData( tmp.getJSONObject("preferences") ));
						} else {
							result = profileResponse;
						}
					}

					if( errorCnt == 0 ) {
						ApiResponse fleetDataResponse = api.getAccountFleetData();
						if( fleetDataResponse.getErrorCode() == ErrorCode.OK ) {
							JSONObject fleetJson = JsonTools.getJSONObject( fleetDataResponse.getJSONObject(), "data" );
							OfficeData office = new OfficeData();
							office.set( fleetJson );
						} else {
							result = fleetDataResponse;
							errorCnt++;
						}
					}

					if( errorCnt == 0 ) {
						ApiResponse vehicleResponse = api.getVehicleTypes();
						if( vehicleResponse.getErrorCode() == ErrorCode.OK ) {
							VehicleData.removeAll();

							JSONArray vehicles = JsonTools.getJSONArray( vehicleResponse.getJSONObject(), "vehicle_types" );
							for( int i=0; i<vehicles.length(); i++ ) {
								VehicleData v = new VehicleData( (JSONObject)vehicles.get(i) );
								v.insert();
							}
						} else {
							result = vehicleResponse;
							errorCnt++;
						}
					}

					if( Office.isPaymentTokenSupportEnabled() ) {

						String userPk = TDApplication.getSessionManager().getAccountData().getPk();

						ApiResponse cardsResponse = api.braintreeWrapperCardList(userPk);
						if( cardsResponse.getErrorCode() == ErrorCode.OK ) {
							CardData.deleteAll();

							JSONArray tokens = JsonTools.getJSONArray( cardsResponse.getJSONObject(), "cards");
							for(int i=0; i<tokens.length(); i++ ) {
								CardData tmp = new CardData( tokens.getJSONObject(i) );
								tmp.insert();
							}
						} else {
							result = cardsResponse;
							errorCnt++;
						}
					}

					if( errorCnt == 0 ) {
						result.setErrorCode(ErrorCode.OK);
					}

				} else {
					result = tokensApiResponse;
				}

			} catch( Exception e ) {
				e.printStackTrace();
			}

			return result;
		}

		@Override
		protected void onPostExecute(ApiResponse apiResponse) {
			if( apiResponse.getErrorCode() == ErrorCode.OK ) {
				mHostActivity.oAuthAuthenticated();
			} else {
				lockUI(false);
				showDialog(GenericDialogFragment.DIALOG_TYPE_ERROR, getString(R.string.dialog_error_title), apiResponse.getErrorMessage() );

				TDApplication.getSessionManager().doLogout();
			}
		}
	}

}

package com.whiterabbit.postman.commands;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.whiterabbit.postman.ServerInteractionHelper;
import com.whiterabbit.postman.exceptions.PostmanException;
import com.whiterabbit.postman.exceptions.ResultParseException;
import com.whiterabbit.postman.oauth.OAuthHelper;
import com.whiterabbit.postman.oauth.OAuthServiceInfo;
import com.whiterabbit.postman.utils.Constants;
import org.scribe.exceptions.OAuthException;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Verb;

import java.util.concurrent.TimeUnit;


/**
 * Server command implementation intended to be used to interact with a rest server
 * @author fede
 *
 */
public class RestServerCommand extends ServerCommand implements RequestExecutor  {
    private final RestServerRequest mFirstStrategy;
    private final Parcelable[] mStrategies; // must be a Parcelable[] instead of RestServerRequest[] because I wouldn't be
                                            // able to read it (can't cast Parcelable[] to RestServerRequest[] )

    private OAuthRequest mMockedRequest;

    /**
     * Constructor
     */
    public RestServerCommand(RestServerRequest firstStrategy, RestServerRequest... otherStrategies){
        mFirstStrategy = firstStrategy;
        mStrategies = otherStrategies;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(mFirstStrategy, 0);
        parcel.writeParcelableArray(mStrategies, 0);
    }

    protected RestServerCommand(Parcel in){
        mFirstStrategy = in.readParcelable(RestServerRequest.class.getClassLoader());
        mStrategies = in.readParcelableArray(RestServerRequest.class.getClassLoader());

    }

    public static final Creator<RestServerCommand> CREATOR
            = new Creator<RestServerCommand>() {
        public RestServerCommand createFromParcel(Parcel in) {
            return new RestServerCommand(in);
        }

        public RestServerCommand[] newArray(int size) {
            return new RestServerCommand[size];
        }
    };





    /**
     * Utility method to be used for mocking up request objects
     * inside unit tests
     * @param v
     * @param url
     * @return
     */
    protected OAuthRequest getRequest(Verb v, String url){
        if(mMockedRequest != null){
            return mMockedRequest;
        }
        return new OAuthRequest(v, url);
    }

    /**
     * Utility method to provide a mocked request instead of connecting to the
     * real server. To be used for testing purpouses only
     * @param r
     */
    public void setMockedRequest(OAuthRequest r){
        mMockedRequest = r;
    }


	/**
	 * The real execution of the command. Performs the basic rest interaction
	 */
	@Override
	public void execute(Context c) {
        ServerInteractionHelper.getInstance(c).enableHttpResponseCache(c); // this looks to be the best place

        try{
            executeRequest(mFirstStrategy, c);

            for(Parcelable p : mStrategies){
                executeRequest((RestServerRequest) p, c);
            }
            notifyResult("Ok",  c);

        } catch (PostmanException e) {
            notifyError(e.getMessage(), c);
            return;
        }catch (OAuthException e){
            notifyError(e.getMessage(), c);
            return;
        }

    }


    @Override
    public void executeRequest(RestServerRequest s, Context c) throws PostmanException {
        try{
            OAuthRequest request = getRequest(s.getVerb(), s.getUrl());
            s.addParamsToRequest(request);

            request.setConnectTimeout(5, TimeUnit.SECONDS); // 5 seconds seems a reasonable timeout
            request.setReadTimeout(10, TimeUnit.SECONDS);

            String signer = s.getOAuthSigner();
            if(signer != null){
                OAuthServiceInfo authService  = OAuthHelper.getInstance().getRegisteredService(signer, c);
                authService.getService().signRequest(authService.getAccessToken(), request);
            }
            Response response = request.send();
            handleResponse(s, response.getCode(), response, c);
        }catch(OAuthException e){
            Log.e(Constants.LOG_TAG, "Exception while executing " + getRequestId() + e.getMessage());
            s.onOAuthExceptionThrown(e);
            Throwable cause = e.getCause();
            if(cause != null && cause.getMessage() != null && cause.getMessage().equals("No authentication challenges found")){
                Log.d(Constants.LOG_TAG, cause.getMessage());
                // TODO Invalidate token ?
            }
            throw e;
        }
    }



    private void handleResponse(RestServerRequest strategy, int statusCode, Response response, Context c) throws PostmanException {

        if(statusCode >= 200 && statusCode < 300){ // success
            try {
                strategy.onHttpResult(response, statusCode, this, c);
            }catch(ResultParseException e){
                notifyError("Failed to parse result " + e.getMessage(), c);
                Log.e(Constants.LOG_TAG, "Result parse failed: " + response);
            }
        }else if(statusCode >= 400 && statusCode < 600){ // error
            strategy.onHttpError(statusCode, this, c);
            switch(statusCode){
                case 404:
                    throw new PostmanException("Not found");
                case 401:
                    throw new PostmanException("No permission");
                    // TODO Invalidate token ??
                default:
                    throw new PostmanException("Generic error " + statusCode);
            }
        } else {
            Log.e(Constants.LOG_TAG, "Unexpected http result " + statusCode);
            throw new PostmanException("Generic error " + statusCode);
        }
    }

}

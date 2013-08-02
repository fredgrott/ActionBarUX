package com.whiterabbit.postman.exceptions;

@SuppressWarnings("serial")
public class ResultParseException extends Exception {
    public ResultParseException(String reason){
        super(reason);
    }

}

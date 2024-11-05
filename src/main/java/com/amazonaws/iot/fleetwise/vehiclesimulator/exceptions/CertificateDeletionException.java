package com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions;


public final class CertificateDeletionException extends Exception {
    public CertificateDeletionException( String message,  Throwable cause) {
        super(message, cause);
    }

    public CertificateDeletionException( String message) {
        super(message);
    }
}

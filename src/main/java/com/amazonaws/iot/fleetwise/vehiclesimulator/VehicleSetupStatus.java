package com.amazonaws.iot.fleetwise.vehiclesimulator;

import java.util.Set;

import lombok.Data;


public record VehicleSetupStatus(Set<String> successList, Set<String> failedList) {

    /*public VehicleSetupStatus( Set<String> successList,  Set<String> failedList) {
        this.successList = successList;
        this.failedList = failedList;
    }


    public final Set<String> getSuccessList() {
        return this.successList;
    }


    public final Set<String> getFailedList() {
        return this.failedList;
    }


    public final Set<String> component1() {
        return this.successList;
    }


    public final Set<String> component2() {
        return this.failedList;
    }


    public final VehicleSetupStatus copy( Set<String>  successList,  Set<String>  failedList) {
        Intrinsics.checkNotNullParameter(successList, "successList");
        Intrinsics.checkNotNullParameter(failedList, "failedList");
        return new VehicleSetupStatus(successList, failedList);
    }

    public String toString() {
        return "VehicleSetupStatus(successList=" + this.successList + ", failedList=" + this.failedList + ')';
    }

    public int hashCode() {
        int result = this.successList.hashCode();
        result = result * 31 + this.failedList.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof VehicleSetupStatus)) {
            return false;
        } else {
            VehicleSetupStatus var2 = (VehicleSetupStatus)other;
            if (!Intrinsics.areEqual(this.successList, var2.successList)) {
                return false;
            } else {
                return Intrinsics.areEqual(this.failedList, var2.failedList);
            }
        }
    }*/
}

package it.pruefert.headvault.economy;

/**
 * Outcome of an attempted purchase. Pure data shared between the economy logic and the UI/access
 * layers.
 */
public record PurchaseResult(Status status, Cost cost, String detail) {

    public enum Status {
        /** Paid successfully. */
        SUCCESS,
        /** Free (mode FREE or a {@code headvault.free-bypass} permission holder). */
        FREE,
        /** Player could not afford the cost; nothing was deducted. */
        INSUFFICIENT_FUNDS,
        /** Something went wrong (e.g. unknown item id); nothing was deducted. */
        ERROR
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.FREE;
    }

    public static PurchaseResult success(Cost cost) {
        return new PurchaseResult(Status.SUCCESS, cost, "");
    }

    public static PurchaseResult free() {
        return new PurchaseResult(Status.FREE, Cost.free(), "");
    }

    public static PurchaseResult insufficient(Cost cost) {
        return new PurchaseResult(Status.INSUFFICIENT_FUNDS, cost, "");
    }

    public static PurchaseResult error(String detail) {
        return new PurchaseResult(Status.ERROR, Cost.free(), detail);
    }
}

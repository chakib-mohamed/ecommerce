package the.chak.ecommerce.products.boundary.dto;

public enum ErrorCode {
    INTERNAL_SERVER_ERROR(500), BAD_REQUEST(400), NOT_FOUND(404);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

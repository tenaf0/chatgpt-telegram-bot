package hu.garaba.util;

public class Either<A,B> {
    private final boolean type;
    private final Object obj;

    private Either(Object obj, boolean type) {
        this.type = type;
        this.obj = obj;
    }

    public static <A,B> Either<A,B> left(A obj) {
        return new Either<>(obj, false);
    }

    public static <A,B> Either<A,B> right(B obj) {
        return new Either<>(obj, true);
    }

    @SuppressWarnings("unchecked")
    public A left() {
        if (type) {
            throw new IllegalStateException("You have requested the left option, but the right one was set");
        }
        return (A) obj;
    }

    @SuppressWarnings("unchecked")
    public B right() {
        if (!type) {
            throw new IllegalStateException("You have requested the right option, but the left one was set");
        }
        return (B) obj;
    }

    public boolean isLeft() {
        return !type;
    }

    public boolean isRight() {
        return type;
    }
}

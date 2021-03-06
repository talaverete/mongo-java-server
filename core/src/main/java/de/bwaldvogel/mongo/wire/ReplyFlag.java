package de.bwaldvogel.mongo.wire;

public enum ReplyFlag implements Flag {
    CURSOR_NOT_FOUND(0),
    QUERY_FAILURE(1),
    SHARD_CONFIG_STALE(2),
    AWAIT_CAPABLE(3);

    private int value;

    private ReplyFlag(int bit) {
        this.value = 1 << bit;
    }

    @Override
    public boolean isSet(int flags) {
        return (flags & value) == value;
    }

    @Override
    public int removeFrom(int flags) {
        return flags - value;
    }

}

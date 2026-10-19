package com.btsou.search;

/** Six taps, with at most 1.5 seconds between adjacent taps. No persisted state. */
public final class TapSequence {
    private int count=0;
    private long last=0;
    public boolean tap(long monotonicNanos){
        if(count==0||monotonicNanos<last||monotonicNanos-last>1_500_000_000L)count=0;
        last=monotonicNanos;
        if(++count<6)return false;
        count=0;return true;
    }
}

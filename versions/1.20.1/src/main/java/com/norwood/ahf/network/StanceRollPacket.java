package com.norwood.ahf.network;

/** Owner's prone roll ({@code dir} +1 right); the server starts or refuses it, synced with the targets. */
public record StanceRollPacket(int dir) {
}

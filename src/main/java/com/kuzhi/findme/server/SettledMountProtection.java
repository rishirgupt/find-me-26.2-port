package com.kuzhi.findme.server;

import java.util.UUID;

record SettledMountProtection(UUID mountUuid, long untilTick) {
}

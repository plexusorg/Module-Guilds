package dev.plex.util;

import lombok.Data;

public record CustomLocation(String worldName, double x, double y, double z, float yaw, float pitch)
{
}

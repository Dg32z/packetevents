/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2022 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.retrooper.packetevents.util;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Content-validated conversion cache keyed by the identity of an underlying
 * platform object (e.g. an NMS ItemStack handle).
 * <p>
 * Entries are validated by a {@code count} and a {@code patch} reference: any change to the
 * source object that goes through a count mutation or a patch replacement invalidates the entry.
 * Hits are handed out through the configured copier so callers can never mutate the cached value.
 * Stale entries are removed automatically once the handle is garbage collected (weak keys
 * backed by a reference queue).
 *
 * @param <T> converted value type
 */
public final class ConversionCache<T> {

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final Map<HandleKey, Entry<T>> cache = new ConcurrentHashMap<>();
    private final Function<T, T> copier;

    public ConversionCache(Function<T, T> copier) {
        this.copier = copier;
    }

    /**
     * Returns a cached conversion when the handle identity, count and patch reference all match,
     * otherwise runs the slow path, stores its result and returns it.
     */
    public T getOrConvert(Object handle, int count, @Nullable Object patch, Supplier<T> slowPath) {
        if (handle == null) {
            return slowPath.get();
        }

        expunge();

        HandleKey key = new HandleKey(handle, referenceQueue);
        Entry<T> entry = cache.get(key);
        if (entry != null && entry.count == count && entry.patch == patch) {
            return copier.apply(entry.value);
        }

        T converted = slowPath.get();
        cache.put(key, new Entry<>(count, patch, converted));
        return copier.apply(converted);
    }

    /**
     * Test helper: number of live entries after expunging stale references.
     */
    int size() {
        expunge();
        return cache.size();
    }

    private void expunge() {
        Object reference;
        while ((reference = referenceQueue.poll()) != null) {
            cache.remove(reference);
        }
    }

    private static final class Entry<T> {
        final int count;
        final Object patch;
        final T value;

        Entry(int count, Object patch, T value) {
            this.count = count;
            this.patch = patch;
            this.value = value;
        }
    }

    private static final class HandleKey extends WeakReference<Object> {
        private final int hash;

        HandleKey(Object handle, ReferenceQueue<Object> queue) {
            super(handle, queue);
            this.hash = System.identityHashCode(handle);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HandleKey)) {
                return false;
            }
            HandleKey that = (HandleKey) other;
            Object mine = get();
            Object theirs = that.get();
            return mine != null && mine == theirs;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}

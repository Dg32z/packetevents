/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2024 retrooper and contributors
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

package com.github.retrooper.packetevents.protocol.component.builtin.item;

import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ItemLore {

    public static final ItemLore EMPTY = new ItemLore(Collections.emptyList());

    private List<Component> lines;
    private List<NBT> rawLines;
    private AdventureSerializer serializer;
    private boolean converted;

    public ItemLore(List<Component> lines) {
        this.lines = lines;
        this.converted = true;
    }

    private ItemLore() {
    }

    public static ItemLore read(PacketWrapper<?> wrapper) {
        ItemLore lore = new ItemLore();
        lore.rawLines = wrapper.readList(PacketWrapper::readNBTRaw);
        lore.serializer = wrapper.getSerializers();
        return lore;
    }

    public static void write(PacketWrapper<?> wrapper, ItemLore lore) {
        if (lore.converted) {
            wrapper.writeList(lore.lines, PacketWrapper::writeComponent);
        } else {
            wrapper.writeList(lore.rawLines, PacketWrapper::writeNBTRaw);
        }
    }

    private void ensureConverted() {
        if (converted) {
            return;
        }
        synchronized (this) {
            if (converted) {
                return;
            }
            List<Component> convertedLines = new ArrayList<>(rawLines.size());
            AdventureSerializer s = serializer;
            for (NBT nbt : rawLines) {
                convertedLines.add(s.fromNbtTag(nbt));
            }
            this.lines = convertedLines;
            this.rawLines = null;
            this.serializer = null;
            this.converted = true;
        }
    }

    public void addLine(Component line) {
        ensureConverted();
        this.lines.add(line);
    }

    public List<Component> getLines() {
        ensureConverted();
        return this.lines;
    }

    public void setLines(List<Component> lines) {
        this.lines = lines;
        this.rawLines = null;
        this.serializer = null;
        this.converted = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemLore)) return false;
        ItemLore itemLore = (ItemLore) obj;
        return getLines().equals(itemLore.getLines());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLines());
    }

    @Override
    public String toString() {
        return "ItemLore{lines=" + getLines() + '}';
    }
}

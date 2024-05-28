/*
 * Copyright (C) 2018-2024 Alexander Schmid
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.github.alexqp.phantomspawncontrol.command.giantPhantoms.SetSubCmd.LootTableSubCmd;

import com.github.alexqp.commons.command.AlexSubCommand;
import com.github.alexqp.phantomspawncontrol.command.AsyncContainerSubCmd;
import com.github.alexqp.phantomspawncontrol.command.giantPhantoms.SetSubCmd.SetSubCmd;
import net.md_5.bungee.api.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class LootTableSubCmd extends AsyncContainerSubCmd {

    public LootTableSubCmd(@NotNull SetSubCmd parent) {
        super("lootTable", new TextComponent("sets a giant_phantom's lootTable"), parent);
        HashSet<AlexSubCommand> subCmds = new HashSet<>();
        subCmds.add(new AddSubCmd(this));
        subCmds.add(new RemoveSubCmd(this));
        this.addSubCmds(subCmds);
    }
}

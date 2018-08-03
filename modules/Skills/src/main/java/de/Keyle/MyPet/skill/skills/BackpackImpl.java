/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2018 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.skill.skills;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.event.MyPetInventoryActionEvent;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.skill.skills.Backpack;
import de.Keyle.MyPet.api.util.inventory.CustomInventory;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.keyle.knbt.TagCompound;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class BackpackImpl implements Backpack {
    protected int rows = 0;
    protected boolean dropOnDeath = false;

    protected CustomInventory inv;
    protected MyPet myPet;

    public BackpackImpl(MyPet myPet) {
        this.myPet = myPet;
        inv = MyPetApi.getCompatUtil().getComapatInstance(CustomInventory.class, "util.inventory", "CustomInventory");
    }

    public CustomInventory getInventory() {
        return inv;
    }

    public MyPet getMyPet() {
        return myPet;
    }

    public String toPrettyString() {
        return "" + ChatColor.GOLD + rows + ChatColor.RESET + " " + Translation.getString("Name.Rows", myPet.getOwner());
    }

    public boolean activate() {
        if (rows > 0) {
            if (myPet.getOwner().getPlayer().isSleeping()) {
                myPet.getOwner().sendMessage(Translation.getString("Message.No.CanUse", myPet.getOwner()));
                return false;
            }
            if (myPet.getOwner().getPlayer().getGameMode() == GameMode.CREATIVE && !Configuration.Skilltree.Skill.Inventory.OPEN_IN_CREATIVE && !Permissions.has(myPet.getOwner().getPlayer(), "MyPet.admin", false)) {
                myPet.getOwner().sendMessage(Translation.getString("Message.Skill.Inventory.Creative", myPet.getOwner()));
                return false;
            }
            MyPetInventoryActionEvent event = new MyPetInventoryActionEvent(myPet, MyPetInventoryActionEvent.Action.Open);
            Bukkit.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.No.AllowedHere", myPet.getOwner()), myPet.getPetName()));
                return false;
            }
            if (!myPet.getLocation().get().getBlock().isLiquid()) {
                inv.setName(myPet.getPetName());
                openInventory(myPet.getOwner().getPlayer());
                return true;
            } else {
                myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skill.Inventory.Swimming", myPet.getOwner()), myPet.getPetName()));
                return false;
            }
        } else {
            myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skill.Inventory.NotAvailable", myPet.getOwner()), myPet.getPetName()));
            return false;
        }
    }

    public void openInventory(Player p) {
        inv.open(p);
    }

    public void closeInventory() {
        inv.close();
    }

    public void load(TagCompound tag) {
        inv.load(tag);
    }

    public TagCompound save() {
        TagCompound nbtTagCompound = new TagCompound();
        inv.save(nbtTagCompound);
        return nbtTagCompound;
    }

    public boolean isActive() {
        return rows > 0;
    }

    @Override
    public void reset() {
        rows = 0;
        dropOnDeath = false;
    }

    public boolean getDropOnDeath() {
        return dropOnDeath;
    }

    @Override
    public void setDropOnDeath(boolean drop) {
        dropOnDeath = drop;
    }

    @Override
    public int getRows() {
        return rows;
    }

    @Override
    public void setRows(int rows) {
        this.rows = rows;
    }

    @Override
    public String toString() {
        return "BackpackImpl{" +
                "rows=" + rows +
                ", dropOnDeath=" + dropOnDeath +
                '}';
    }
}
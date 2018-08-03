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
import de.Keyle.MyPet.api.gui.IconMenu;
import de.Keyle.MyPet.api.gui.IconMenuItem;
import de.Keyle.MyPet.api.skill.skills.Beacon;
import de.Keyle.MyPet.api.util.inventory.meta.SkullMeta;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.keyle.knbt.*;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

import static org.bukkit.ChatColor.*;
import static org.bukkit.Material.*;

public class BeaconImpl implements Beacon {

    SkullMeta disabledMeta = new SkullMeta();
    SkullMeta partyMeta = new SkullMeta();
    SkullMeta everyoneMeta = new SkullMeta();
    org.bukkit.inventory.meta.SkullMeta ownerMeta;

    protected int duration = 0;
    protected int range = 0;
    protected int selectableBuffs = 0;
    protected MyPet myPet;
    protected boolean active = false;
    protected int hungerDecreaseTimer;
    protected BuffReceiver receiver = BuffReceiver.Owner;
    protected Map<Buff, Integer> buffLevel = new HashMap<>();
    protected int beaconTimer = 0;
    protected List<Buff> selectedBuffs = new ArrayList<>();

    public BeaconImpl(MyPet myPet) {
        this.myPet = myPet;
        hungerDecreaseTimer = Configuration.Skilltree.Skill.Beacon.HUNGER_DECREASE_TIME;
    }

    public MyPet getMyPet() {
        return myPet;
    }

    public boolean isActive() {
        if (selectableBuffs == 0 || range == 0) {
            return false;
        }
        for (int amp : buffLevel.values()) {
            if (amp > 0) {
                return duration > 0 && range > 0;
            }
        }
        return false;
    }

    @Override
    public void reset() {
        duration = 0;
        range = 0;
        selectableBuffs = 0;
        buffLevel.clear();
    }

    public boolean activate() {
        final Player owner = myPet.getOwner().getPlayer();

        final BeaconImpl beacon = this;
        String title = RESET + Translation.getString("Name.Skill.Beacon", myPet.getOwner());
        IconMenu menu = new IconMenu(title, new IconMenu.OptionClickEventHandler() {
            @Override
            public void onOptionClick(IconMenu.OptionClickEvent event) {
                event.setWillClose(false);
                event.setWillDestroy(false);

                if (getMyPet().getStatus() != MyPet.PetState.Here) {
                    return;
                }

                IconMenu menu = event.getMenu();

                switch (event.getPosition()) {
                    case 5:
                        event.setWillClose(true);
                        event.setWillDestroy(true);
                        return;
                    case 4:
                        if (active) {
                            menu.getOption(4)
                                    .setMaterial(REDSTONE_BLOCK)
                                    .setTitle(Util.formatText(Translation.getString("Message.Skill.Beacon.Effect", myPet.getOwner().getLanguage()), RED + Translation.getString("Name.Off", myPet.getOwner().getLanguage())))
                                    .setLore(RESET + Translation.getString("Message.Skill.Beacon.ClickOn", myPet.getOwner().getLanguage()));
                            active = false;
                        } else {
                            menu.getOption(4)
                                    .setMaterial(EMERALD_BLOCK)
                                    .setTitle(Util.formatText(Translation.getString("Message.Skill.Beacon.Effect", myPet.getOwner().getLanguage()), GREEN + Translation.getString("Name.On", myPet.getOwner().getLanguage())))
                                    .setLore(RESET + Translation.getString("Message.Skill.Beacon.ClickOff", myPet.getOwner().getLanguage()));
                            active = true;
                        }
                        menu.update();
                        break;
                    case 3:
                        beacon.active = active;
                        beacon.selectedBuffs.clear();
                        beacon.selectedBuffs.addAll(selectedBuffs);
                        beacon.receiver = receiver;
                        event.setWillClose(true);
                        event.setWillDestroy(true);
                        break;
                    case 21:
                        if (receiver != BuffReceiver.Owner) {
                            menu.getOption(21).setMeta(ownerMeta, false, false);
                            if (menu.getOption(22) != null) {
                                menu.getOption(22).setMeta(partyMeta);
                            }
                            menu.getOption(23).setMeta(disabledMeta);
                            receiver = BuffReceiver.Owner;
                            menu.update();
                        }
                        break;
                    case 22:
                        if (receiver != BuffReceiver.Party) {
                            menu.getOption(21).setMeta(disabledMeta);
                            menu.getOption(22).setMeta(partyMeta);
                            menu.getOption(23).setMeta(disabledMeta);
                            receiver = BuffReceiver.Party;
                            menu.update();
                        }
                        break;
                    case 23:
                        if (receiver != BuffReceiver.Everyone) {
                            menu.getOption(21).setMeta(disabledMeta);
                            if (menu.getOption(22) != null) {
                                menu.getOption(22).setMeta(disabledMeta);
                            }
                            menu.getOption(23).setMeta(everyoneMeta);
                            receiver = BuffReceiver.Everyone;
                            menu.update();
                        }
                        break;
                    default:
                        Buff selectedBuff = Buff.getBuffAtPosition(event.getPosition());
                        if (selectedBuff != null) {

                            if (selectableBuffs > 1) {
                                if (selectedBuffs.indexOf(selectedBuff) != -1) {
                                    selectedBuffs.remove(selectedBuff);
                                    menu.getOption(selectedBuff.getPosition()).setGlowing(false);
                                    if (selectableBuffs > selectedBuffs.size()) {
                                        menu.setOption(13, new IconMenuItem().setMaterial(POTION).setTitle(BLUE + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), selectableBuffs - selectedBuffs.size())).setAmount(selectableBuffs - selectedBuffs.size()));
                                    } else {
                                        menu.setOption(13, new IconMenuItem().setMaterial(GLASS_BOTTLE).setTitle(GRAY + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), 0)));
                                    }
                                    menu.update();
                                } else if (selectableBuffs > selectedBuffs.size()) {
                                    selectedBuffs.add(selectedBuff);
                                    menu.getOption(selectedBuff.getPosition()).setGlowing(true);
                                    if (selectableBuffs > selectedBuffs.size()) {
                                        menu.setOption(13, new IconMenuItem().setMaterial(POTION).setTitle(BLUE + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), selectableBuffs - selectedBuffs.size())).setAmount(selectableBuffs - selectedBuffs.size()));
                                    } else {
                                        menu.setOption(13, new IconMenuItem().setMaterial(GLASS_BOTTLE).setTitle(GRAY + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), 0)));
                                    }
                                    menu.update();
                                } else {
                                    break;
                                }

                                if (selectableBuffs > selectedBuffs.size()) {
                                    menu.setOption(13, new IconMenuItem().setMaterial(POTION).setTitle(BLUE + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), selectableBuffs - selectedBuffs.size())).setAmount(selectableBuffs - selectedBuffs.size()));
                                } else {
                                    menu.setOption(13, new IconMenuItem().setMaterial(GLASS_BOTTLE).setTitle(GRAY + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), 0)));
                                }
                            } else {
                                if (!selectedBuffs.contains(selectedBuff)) {
                                    if (selectedBuffs.size() != 0 && menu.getOption(selectedBuff.getPosition()) != null) {
                                        menu.getOption(selectedBuffs.get(0).getPosition()).setGlowing(false);
                                        selectedBuffs.clear();
                                    }
                                    selectedBuffs.add(selectedBuff);
                                    menu.getOption(selectedBuff.getPosition()).setGlowing(true);
                                    menu.update();
                                }
                            }
                        }
                }
            }
        }, MyPetApi.getPlugin());

        if (beacon.active) {
            menu.setOption(4, new IconMenuItem()
                    .setMaterial(EMERALD_BLOCK)
                    .setTitle(Util.formatText(Translation.getString("Message.Skill.Beacon.Effect", myPet.getOwner().getLanguage()), GREEN + Translation.getString("Name.On", myPet.getOwner().getLanguage())))
                    .addLoreLine(RESET + Translation.getString("Message.Skill.Beacon.ClickOff", myPet.getOwner().getLanguage()))
            );
        } else {
            menu.setOption(4, new IconMenuItem()
                    .setMaterial(REDSTONE_BLOCK)
                    .setTitle(Util.formatText(Translation.getString("Message.Skill.Beacon.Effect", myPet.getOwner().getLanguage()), RED + Translation.getString("Name.Off", myPet.getOwner().getLanguage())))
                    .addLoreLine(RESET + Translation.getString("Message.Skill.Beacon.ClickOn", myPet.getOwner().getLanguage()))
            );
        }

        menu.setOption(3, new IconMenuItem().setMaterial(STAINED_GLASS_PANE).setData(5).setTitle(GREEN + Translation.getString("Name.Done", myPet.getOwner().getLanguage())));
        menu.setOption(5, new IconMenuItem().setMaterial(STAINED_GLASS_PANE).setData(14).setTitle(RED + Translation.getString("Name.Cancel", myPet.getOwner().getLanguage())));

        if (receiver == BuffReceiver.Owner) {
            menu.setOption(21, new IconMenuItem().setMaterial(SKULL_ITEM).setData(3).setTitle(GOLD + Translation.getString("Name.Owner", myPet.getOwner().getLanguage())).setMeta(ownerMeta, false, false));
        } else {
            menu.setOption(21, new IconMenuItem().setMaterial(SKULL_ITEM).setData(3).setTitle(GOLD + Translation.getString("Name.Owner", myPet.getOwner().getLanguage())).setMeta(disabledMeta));
        }
        if (Configuration.Skilltree.Skill.Beacon.PARTY_SUPPORT && MyPetApi.getHookHelper().isInParty(getMyPet().getOwner().getPlayer())) {
            if (receiver != BuffReceiver.Party) {
                menu.setOption(22, new IconMenuItem().setMaterial(SKULL_ITEM).setData(3).setTitle(GOLD + Translation.getString("Name.Party", myPet.getOwner().getLanguage())).setMeta(partyMeta));
            } else {
                menu.setOption(22, new IconMenuItem().setMaterial(SKULL_ITEM).setData(3).setTitle(GOLD + Translation.getString("Name.Party", myPet.getOwner().getLanguage())).setMeta(disabledMeta));
            }
        }
        if (receiver == BuffReceiver.Everyone) {
            menu.setOption(23, new IconMenuItem().setMaterial(SKULL_ITEM).setData(3).setTitle(GOLD + Translation.getString("Name.Everyone", myPet.getOwner().getLanguage())).setMeta(everyoneMeta));
        } else {
            menu.setOption(23, new IconMenuItem().setMaterial(SKULL_ITEM).setData(3).setTitle(GOLD + Translation.getString("Name.Everyone", myPet.getOwner().getLanguage())).setMeta(disabledMeta));
        }

        if (buffLevel.get(1) > 0) {
            menu.setOption(0, new IconMenuItem().setMaterial(LEATHER_BOOTS).setAmount(buffLevel.get(1)).setTitle(GOLD + Translation.getString("Name." + Buff.Speed.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(1))));
        }
        if (buffLevel.get(3) > 0) {
            menu.setOption(9, new IconMenuItem().setMaterial(GOLD_PICKAXE).setAmount(buffLevel.get(3)).setTitle(GOLD + Translation.getString("Name." + Buff.Haste.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(3))));
        }
        if (buffLevel.get(5) > 0) {
            menu.setOption(18, new IconMenuItem().setMaterial(DIAMOND_SWORD).setAmount(buffLevel.get(5)).setTitle(GOLD + Translation.getString("Name." + Buff.Strength.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(5))));
        }
        if (buffLevel.get(8) > 0) {
            menu.setOption(1, new IconMenuItem().setMaterial(FIREWORK).setAmount(buffLevel.get(8)).setTitle(GOLD + Translation.getString("Name." + Buff.JumpBoost.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(8))));
        }
        if (buffLevel.get(10) > 0) {
            menu.setOption(10, new IconMenuItem().setMaterial(APPLE).setAmount(buffLevel.get(10)).setTitle(GOLD + Translation.getString("Name." + Buff.Regeneration.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(10))));
        }
        if (buffLevel.get(11) > 0) {
            menu.setOption(19, new IconMenuItem().setMaterial(DIAMOND_CHESTPLATE).setAmount(buffLevel.get(11)).setTitle(GOLD + Translation.getString("Name." + Buff.Resistance.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(11))));
        }
        if (buffLevel.get(12) > 0) {
            menu.setOption(7, new IconMenuItem().setMaterial(LAVA_BUCKET).setAmount(buffLevel.get(12)).setTitle(GOLD + Translation.getString("Name." + Buff.FireResistance.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(12))));
        }
        if (buffLevel.get(13) > 0) {
            menu.setOption(16, new IconMenuItem().setMaterial(RAW_FISH).setAmount(buffLevel.get(13)).setTitle(GOLD + Translation.getString("Name." + Buff.WaterBreathing.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(13))));
        }
        if (buffLevel.get(14) > 0) {
            menu.setOption(25, new IconMenuItem().setMaterial(EYE_OF_ENDER).setAmount(buffLevel.get(14)).setTitle(GOLD + Translation.getString("Name." + Buff.Invisibility.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(14))));
        }
        if (buffLevel.get(16) > 0) {
            menu.setOption(8, new IconMenuItem().setMaterial(TORCH).setAmount(buffLevel.get(16)).setTitle(GOLD + Translation.getString("Name." + Buff.NightVision.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(16))));
        }
        if (MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.9") >= 0) {
            if (buffLevel.get(26) > 0) {
                menu.setOption(17, new IconMenuItem().setMaterial(DIAMOND).setAmount(buffLevel.get(26)).setTitle(GOLD + Translation.getString("Name." + Buff.Luck.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(26))));
            }
        }
        /*
        if (buffLevel.get(21) > 0) {
            menu.setOption(17, new IconMenuItem().setMaterial(GOLDEN_APPLE).setAmount(buffLevel.get(21)).setTitle(GOLD + Translation.getString("Name." + buffNames.get(21), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(21))));
        }
        */
        if (buffLevel.get(22) > 0) {
            menu.setOption(26, new IconMenuItem().setMaterial(SPONGE).setAmount(buffLevel.get(22)).setTitle(GOLD + Translation.getString("Name." + Buff.Absorption.getName(), myPet.getOwner().getLanguage()) + GRAY + " " + Util.decimal2roman(buffLevel.get(22))));
        }

        Iterator<Buff> iterator = selectedBuffs.iterator();
        while (iterator.hasNext()) {
            Buff buff = iterator.next();
            if (buffLevel.containsKey(buff) && buffLevel.get(buff) > 0) {
                menu.getOption(buff.getPosition()).setGlowing(true);
            } else {
                iterator.remove();
            }
        }

        if (selectableBuffs > 1) {
            if (selectableBuffs > selectedBuffs.size()) {
                menu.setOption(13, new IconMenuItem().setMaterial(POTION).setTitle(BLUE + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), selectableBuffs - selectedBuffs.size())).setAmount(selectableBuffs - selectedBuffs.size()));
            } else {
                menu.setOption(13, new IconMenuItem().setMaterial(GLASS_BOTTLE).setTitle(GRAY + Util.formatText(Translation.getString("Message.Skill.Beacon.RemainingBuffs", myPet.getOwner().getLanguage()), 0)));
            }
        }

        menu.open(owner);

        return true;
    }

    public String toPrettyString() {
        String availableBuffs = "";
        for (Buff buff : buffLevel.keySet()) {
            if (buffLevel.get(buff) > 0) {
                if (!availableBuffs.equalsIgnoreCase("")) {
                    availableBuffs += ", ";
                }
                availableBuffs += GOLD + Translation.getString("Name." + buff.getName(), myPet.getOwner().getLanguage());
                availableBuffs += GRAY + " " + Util.decimal2roman(buffLevel.get(buff));
                availableBuffs += ChatColor.RESET;
            }
        }
        return availableBuffs;
    }

    public void schedule() {
        if (myPet.getStatus() == MyPet.PetState.Here && isActive() && active && selectedBuffs.size() != 0 && --beaconTimer <= 0) {
            beaconTimer = 2;

            double range = this.range;

            if (Configuration.HungerSystem.USE_HUNGER_SYSTEM && Configuration.HungerSystem.AFFECT_BEACON_RANGE) {
                range *= (Math.log10(myPet.getSaturation()) / 2);
            }

            if (range < 0.7) {
                return;
            }


            if (selectedBuffs.size() == 0) {
                return;
            }
            if (selectedBuffs.size() > selectableBuffs) {
                selectedBuffs.clear();
            }

            range = range * range;
            MyPetApi.getPlatformHelper().playParticleEffect(myPet.getLocation().get().add(0, 1, 0), "SPELL_WITCH", 0.2F, 0.2F, 0.2F, 0.1F, 5, 20);

            List<Player> members = null;
            if (Configuration.Skilltree.Skill.Beacon.PARTY_SUPPORT && receiver == BuffReceiver.Party) {
                members = MyPetApi.getHookHelper().getPartyMembers(getMyPet().getOwner().getPlayer());
            }
            int duration = this.duration * 20;

            List<PotionEffect> potionEffects = new ArrayList<>();
            for (Buff buff : selectedBuffs) {
                int amplification = buffLevel.get(buff) - 1;
                PotionEffect effect = new PotionEffect(PotionEffectType.getById(buff.getId()), duration, amplification, true, true);
                potionEffects.add(effect);
            }

            Location myPetLocation = this.myPet.getLocation().get();
            targetLoop:
            for (Player player : myPetLocation.getWorld().getPlayers()) {
                if (MyPetApi.getPlatformHelper().distanceSquared(player.getLocation(), myPetLocation) > range) {
                    continue;
                }

                switch (receiver) {
                    case Owner:
                        if (!myPet.getOwner().equals(player)) {
                            continue targetLoop;
                        } else {
                            for (PotionEffect effect : potionEffects) {
                                player.addPotionEffect(effect, true);
                            }
                            MyPetApi.getPlatformHelper().playParticleEffect(player.getLocation().add(0, 1, 0), "SPELL_INSTANT", 0.2F, 0.2F, 0.2F, 0.1F, 5, 20);
                            break targetLoop;
                        }
                    case Everyone:
                        for (PotionEffect effect : potionEffects) {
                            player.addPotionEffect(effect, true);
                        }
                        MyPetApi.getPlatformHelper().playParticleEffect(player.getLocation().add(0, 1, 0), "SPELL_INSTANT", 0.2F, 0.2F, 0.2F, 0.1F, 5, 20);
                        break;
                    case Party:
                        if (Configuration.Skilltree.Skill.Beacon.PARTY_SUPPORT && members != null) {
                            if (members.contains(player)) {
                                for (PotionEffect effect : potionEffects) {
                                    player.addPotionEffect(effect, true);
                                }
                                MyPetApi.getPlatformHelper().playParticleEffect(player.getLocation().add(0, 1, 0), "SPELL_INSTANT", 0.2F, 0.2F, 0.2F, 0.1F, 5, 20);
                            }
                            break;
                        } else {
                            receiver = BuffReceiver.Owner;
                            break targetLoop;
                        }
                }
            }

            if (Configuration.HungerSystem.USE_HUNGER_SYSTEM && Configuration.Skilltree.Skill.Beacon.HUNGER_DECREASE_TIME > 0 && hungerDecreaseTimer-- < 0) {
                myPet.decreaseSaturation(1);
                hungerDecreaseTimer = Configuration.Skilltree.Skill.Beacon.HUNGER_DECREASE_TIME;
            }
        }
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public int getNumberOfBuffs() {
        return selectableBuffs;
    }

    @Override
    public void setNumberOfBuffs(int n) {
        this.selectableBuffs = n;
    }

    @Override
    public int getRange() {
        return range;
    }

    @Override
    public void setRange(int n) {
        this.range = range;
    }

    @Override
    public void setBuffLevel(Buff buff, int level) {
        this.buffLevel.put(buff, level);
    }

    @Override
    public int getBuffLevel(Buff buff) {
        return this.buffLevel.getOrDefault(buff, 0);
    }

    @Override
    public TagCompound save() {
        TagCompound nbtTagCompound = new TagCompound();
        nbtTagCompound.getCompoundData().put("Buffs", new TagIntArray(ArrayUtils.toPrimitive(selectedBuffs.toArray(new Integer[selectedBuffs.size()]))));
        nbtTagCompound.getCompoundData().put("Active", new TagByte(this.active));
        nbtTagCompound.getCompoundData().put("Reciever", new TagString(this.receiver.name()));
        return nbtTagCompound;
    }

    @Override
    public void load(TagCompound compound) {
        if (compound.getCompoundData().containsKey("Buff")) {
            Buff selectedBuff = Buff.getBuffByID(compound.getAs("Buff", TagInt.class).getIntData());
            if (selectedBuff != null) {
                this.selectedBuffs.add(selectedBuff);
            }
        }
        if (compound.getCompoundData().containsKey("Buffs")) {
            int[] selectedBuffs = compound.getAs("Buffs", TagIntArray.class).getIntArrayData();
            if (selectedBuffs.length != 0) {
                for (int selectedBuffId : selectedBuffs) {
                    Buff selectedBuff = Buff.getBuffByID(selectedBuffId);
                    if (selectedBuff != null) {
                        this.selectedBuffs.add(selectedBuff);
                    }
                }
            }
        }
        if (compound.getCompoundData().containsKey("Active")) {
            this.active = compound.getAs("Active", TagByte.class).getBooleanData();
        }
        if (compound.getCompoundData().containsKey("Reciever")) {
            this.receiver = BuffReceiver.valueOf(compound.getAs("Reciever", TagString.class).getStringData());
        }
    }

    @Override
    public String toString() {
        return "BeaconImpl{" +
                "duration=" + duration +
                ", range=" + range +
                ", selectableBuffs=" + selectableBuffs +
                ", active=" + active +
                ", receiver=" + receiver +
                ", buffLevel=" + buffLevel +
                ", selectedBuffs=" + selectedBuffs +
                '}';
    }
}
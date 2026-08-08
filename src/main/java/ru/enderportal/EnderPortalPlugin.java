package ru.enderportal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class EnderPortalPlugin extends JavaPlugin implements Listener {
    private Location portal;
    private UUID cursedPlayer;
    private boolean awakening, activated, purpleSky;
    private final Random random = new Random();
    private final Set<String> protectedPortals = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig(); loadState();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("EnderPortal 1.0.0 enabled");
        if (purpleSky) Bukkit.getScheduler().runTaskLater(this, this::sendConfiguredPack, 40L);
    }
    @Override public void onDisable(){ saveState(); }

    private void loadState(){
        String wn=getConfig().getString("state.world", "");
        if(!wn.isBlank()) { World w=Bukkit.getWorld(wn); if(w!=null) portal=new Location(w,getConfig().getInt("state.x"),getConfig().getInt("state.y"),getConfig().getInt("state.z")); }
        activated=getConfig().getBoolean("state.activated",false); awakening=getConfig().getBoolean("state.awakening",false); purpleSky=getConfig().getBoolean("state.purple",false);
        String id=getConfig().getString("state.cursed-player",""); if(!id.isBlank()) try{cursedPlayer=UUID.fromString(id);}catch(IllegalArgumentException ignored){}
    }
    private void saveState(){
        if(portal!=null&&portal.getWorld()!=null){getConfig().set("state.world",portal.getWorld().getName());getConfig().set("state.x",portal.getBlockX());getConfig().set("state.y",portal.getBlockY());getConfig().set("state.z",portal.getBlockZ());}
        getConfig().set("state.activated",activated); getConfig().set("state.awakening",awakening); getConfig().set("state.purple",purpleSky); getConfig().set("state.cursed-player",cursedPlayer==null?"":cursedPlayer.toString()); saveConfig();
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onFramePlace(BlockPlaceEvent e){
        if(e.getBlockPlaced().getType()!=Material.END_PORTAL_FRAME) return;
        Location center=findCenter(e.getBlockPlaced());
        if(center==null || activated || awakening || (portal!=null && getConfig().getBoolean("portal.first-only",true))) return;
        if(isComplete(center)){ portal=center; cursedPlayer=e.getPlayer().getUniqueId(); Bukkit.getScheduler().runTask(this,this::startAwakening); }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent e){
        if(portal==null || activated) return;
        boolean ours=e.getBlocks().stream().anyMatch(b->sameBlock(b.getLocation(),portal));
        if(ours || getConfig().getBoolean("portal.block-new-portals",true)) e.setCancelled(true);
    }

    private Location findCenter(Block frame){
        World w=frame.getWorld(); int y=frame.getY();
        for(int x=frame.getX()-2;x<=frame.getX()+2;x++) for(int z=frame.getZ()-2;z<=frame.getZ()+2;z++){Location c=new Location(w,x,y,z);if(isComplete(c))return c;}
        return null;
    }
    private boolean isComplete(Location c){
        int frames=0,eyes=0;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            if(Math.abs(x)!=2&&Math.abs(z)!=2)continue; Block b=c.getWorld().getBlockAt(c.getBlockX()+x,c.getBlockY(),c.getBlockZ()+z);
            if(b.getType()!=Material.END_PORTAL_FRAME)return false; frames++; if(b.getBlockData() instanceof EndPortalFrame f&&f.hasEye())eyes++;
        } return frames==12&&eyes==12;
    }

    private void startAwakening(){
        if(portal==null||awakening||activated)return; awakening=true; saveState(); World w=portal.getWorld();
        int delay=Math.max(0,getConfig().getInt("portal.activation-delay-seconds",60));
        new BukkitRunnable(){int t=0;public void run(){t++; drawBeams(); earthquake(); if(t>=delay*20){cancel();startRift();}}}.runTaskTimer(this,0,1);
    }
    private void drawBeams(){
        World w=portal.getWorld(); double h=getConfig().getDouble("rift.height",60); double[][] pts={{-2,-2},{-2,2},{2,-2},{2,2}};
        for(double[] pt:pts)for(int i=0;i<10;i++){double y=1+random.nextDouble()*h;w.spawnParticle(Particle.DUST,portal.clone().add(pt[0]*.8,y,pt[1]*.8),4,.08,.08,.08,0,new Particle.DustOptions(Color.fromRGB(190,0,255),2.8f));}
    }
    private void earthquake(){
        if(!getConfig().getBoolean("earthquake.enabled",true))return; World w=portal.getWorld(); double r=getConfig().getDouble("earthquake.radius",0);
        for(Player p:Bukkit.getOnlinePlayers()){if(r>0&&(p.getWorld()!=w||p.getLocation().distanceSquared(portal)>r*r))continue;p.playSound(p.getLocation(),Sound.BLOCK_STONE_BREAK,1.5f,.45f);p.setVelocity(p.getVelocity().add(new Vector((random.nextDouble()-.5)*.08,0.02,(random.nextDouble()-.5)*.08)));}
    }

    private void startRift(){
        World w=portal.getWorld(); int dur=Math.max(1,getConfig().getInt("rift.duration-seconds",20));
        new BukkitRunnable(){int s=0;public void run(){s++;double size=Math.min(getConfig().getDouble("rift.max-size",40),s*getConfig().getDouble("rift.growth-per-second",2));drawRift(size);w.playSound(portal.clone().add(0,60,0),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,3f,.55f+s*.02f);if(s>=dur){cancel();explode();}}}.runTaskTimer(this,0,20);
    }
    private void drawRift(double size){
        World w=portal.getWorld(); Location c=portal.clone().add(0,Math.min(100,getConfig().getDouble("rift.height",60)),0);
        for(int i=0;i<Math.max(50,(int)size*6);i++){double x=(random.nextDouble()-.5)*size;double z=(random.nextDouble()-.5)*Math.max(3,size*.18);w.spawnParticle(Particle.DUST,c.clone().add(x,(random.nextDouble()-.5)*2,z),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(190,0,255),2.5f));}
    }
    private void explode(){
        World w=portal.getWorld(); Location c=portal.clone().add(0,Math.min(100,getConfig().getDouble("rift.height",60)),0);
        w.spawnParticle(Particle.EXPLOSION_EMITTER,c,1);w.spawnParticle(Particle.PORTAL,c,2500,15,8,15,1);w.playSound(c,Sound.ENTITY_ENDER_DRAGON_GROWL,10f,.45f);w.playSound(c,Sound.ENTITY_GENERIC_EXPLODE,8f,.55f);
        int dur=Math.max(1,getConfig().getInt("flash.duration-seconds",10));int amp=Math.max(0,getConfig().getInt("flash.amplifier",10));
        for(Player p:Bukkit.getOnlinePlayers()){p.playSound(p.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,8f,.5f);if(getConfig().getBoolean("flash.enabled",true))p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,dur*20,amp,false,false,false));}
        purpleSky=true; sendConfiguredPack(); corruptGround(); saveState(); Bukkit.getScheduler().runTaskLater(this,this::openPortal,dur*20L);
    }

    private void corruptGround(){
        if(portal==null||!getConfig().getBoolean("corruption.enabled",true))return;World w=portal.getWorld();int r=(int)Math.ceil(getConfig().getDouble("corruption.crater-diameter",10)/2);int depth=Math.max(1,getConfig().getInt("corruption.crater-depth",5));
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){if(dx*dx+dz*dz>r*r*(.6+random.nextDouble()*.3))continue;int d=1+random.nextInt(depth);for(int dy=0;dy<d;dy++)w.getBlockAt(portal.getBlockX()+dx,portal.getBlockY()-dy,portal.getBlockZ()+dz).setType(Material.END_STONE);}
        if(getConfig().getBoolean("corruption.trees",true))for(int i=0;i<getConfig().getInt("corruption.tree-count",8);i++)spawnTree(w,portal.clone().add((random.nextDouble()-.5)*10,1,(random.nextDouble()-.5)*10));
    }
    private void spawnTree(World w,Location b){int h=2+random.nextInt(4);for(int i=0;i<h;i++)w.getBlockAt(b.getBlockX(),b.getBlockY()+i,b.getBlockZ()).setType(Material.CHORUS_PLANT);w.getBlockAt(b.getBlockX(),b.getBlockY()+h,b.getBlockZ()).setType(Material.CHORUS_FLOWER);}

    private void openPortal(){
        World w=portal.getWorld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getBlockAt(portal.getBlockX()+x,portal.getBlockY(),portal.getBlockZ()+z).setType(Material.END_PORTAL);
        activated=true;awakening=false;saveState();w.playSound(portal,Sound.BLOCK_END_PORTAL_SPAWN,8f,1f);
    }

    @EventHandler public void onPearlLaunch(ProjectileLaunchEvent e){
        if(!(e.getEntity() instanceof EnderPearl pearl) || cursedPlayer==null || !cursedPlayer.equals(pearl.getShooter() instanceof Player p?p.getUniqueId():null))return;
        if(!getConfig().getBoolean("curse.enabled",true))return; Player p=(Player)pearl.getShooter();
        if(random.nextDouble()*100<getConfig().getDouble("curse.pearl-fail-chance",25)){e.setCancelled(true);p.sendMessage(ChatColor.DARK_PURPLE+"Проклятие портала исказило жемчуг.");return;}
        if(random.nextDouble()*100<getConfig().getDouble("curse.pearl-deviation-chance",20))pearl.setVelocity(pearl.getVelocity().add(new Vector((random.nextDouble()-.5)*.7,.15,(random.nextDouble()-.5)*.7)));
    }
    @EventHandler public void onMove(PlayerMoveEvent e){
        if(!getConfig().getBoolean("corruption.enabled",true)||portal==null||e.getTo()==null||e.getTo().getWorld()!=portal.getWorld())return;double r=getConfig().getDouble("corruption.radius",10);if(e.getTo().distanceSquared(portal)<=r*r){Player p=e.getPlayer();applyBad(p);}
        if(cursedPlayer!=null&&cursedPlayer.equals(e.getPlayer().getUniqueId())&&getConfig().getBoolean("curse.particles",true))e.getPlayer().getWorld().spawnParticle(Particle.DUST,e.getPlayer().getLocation().add(0,1,0),4,.25,.4,.25,0,new Particle.DustOptions(Color.fromRGB(190,0,255),1.4f));
    }
    private void applyBad(Player p){int d=60; if(getConfig().getBoolean("corruption.bad-effects.weakness",true))p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,d,1,false,false,false));if(getConfig().getBoolean("corruption.bad-effects.slowness",true))p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,d,1,false,false,false));if(getConfig().getBoolean("corruption.bad-effects.mining-fatigue",true))p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,d,1,false,false,false));if(getConfig().getBoolean("corruption.bad-effects.darkness",true))p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,d,0,false,false,false));if(getConfig().getBoolean("corruption.bad-effects.poison",false))p.addPotionEffect(new PotionEffect(PotionEffectType.POISON,d,0,false,false,false));}
    @EventHandler public void onCure(EntityPickupItemEvent e){if(!(e.getEntity() instanceof Player p)||cursedPlayer==null||!cursedPlayer.equals(p.getUniqueId()))return;if(e.getItem().getItemStack().getType()==Material.POISONOUS_POTATO){/* cure is applied on use below */}}
    @EventHandler public void onInventoryClick(InventoryClickEvent e){if(!e.getView().getTitle().equals(ChatColor.DARK_PURPLE+"EnderPortal"))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;if(e.getRawSlot()==11){getConfig().set("portal.block-new-portals",!getConfig().getBoolean("portal.block-new-portals",true));saveConfig();openMenu(p);}}
    private void openMenu(Player p){Inventory inv=Bukkit.createInventory(null,27,ChatColor.DARK_PURPLE+"EnderPortal");inv.setItem(11,new ItemStack(Material.BARRIER));inv.setItem(13,new ItemStack(Material.CLOCK));inv.setItem(15,new ItemStack(Material.AMETHYST_SHARD));p.openInventory(inv);}
    private void sendConfiguredPack(){String url=getConfig().getString("purple-sky.resource-pack-url","");if(url==null||url.isBlank())return;String sha=getConfig().getString("purple-sky.resource-pack-sha1","");for(Player p:Bukkit.getOnlinePlayers()){if(sha==null||sha.isBlank())p.setResourcePack(url);else p.setResourcePack(url,sha);}}
    private boolean sameBlock(Location a,Location b){return a.getWorld()==b.getWorld()&&a.getBlockX()==b.getBlockX()&&a.getBlockY()==b.getBlockY()&&a.getBlockZ()==b.getBlockZ();}

    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){if(!(s instanceof Player p)){s.sendMessage("Только игрок.");return true;}if(!p.hasPermission("enderportal.admin")){p.sendMessage(ChatColor.RED+"Нет прав.");return true;}if(args.length>0&&args[0].equalsIgnoreCase("reset")){portal=null;activated=false;awakening=false;cursedPlayer=null;purpleSky=false;saveState();p.sendMessage(ChatColor.LIGHT_PURPLE+"EnderPortal сброшен.");return true;}openMenu(p);return true;}
}

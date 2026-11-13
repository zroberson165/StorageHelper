package com.storagehelper.gametest;

import com.storagehelper.StorageHelperClient;
import com.storagehelper.chest.*;
import com.storagehelper.logic.SortMode;
import com.storagehelper.logic.SortPlanner;
import com.storagehelper.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;

/** Server-asserted reversals and restricted player-main sorting, through actual UI input. */
final class AdvancedInteractions {
    private AdvancedInteractions() {}
    static void run(ClientGameTestContext ctx, TestSingleplayerContext sp, BlockPos pos) {
        var srv = sp.getServer(); var conn = sp.getConnection();
        srv.runCommand("setblock " + xyz(pos.offset(1,0,0)) + " minecraft:air");
        for (String block : new String[]{"minecraft:chest", "minecraft:purple_shulker_box"}) {
            srv.runCommand("setblock " + xyz(pos) + " minecraft:air");
            srv.runCommand("setblock " + xyz(pos) + " " + block);
            srv.runCommand("kill @e[type=item]");
            srv.runCommand("clear @a");
            blockItem(srv,pos,0,"minecraft:cobblestone",10); blockItem(srv,pos,1,"minecraft:apple",4);
            playerItem(srv,0,"minecraft:cobblestone",6); playerItem(srv,1,"minecraft:dirt",9);
            playerItem(srv,2,"minecraft:apple",3);
            srv.runCommand("item replace entity @a hotbar.0 with minecraft:stick 7");
            open(ctx,conn,pos,block.contains("shulker"));
            int source = playerSlot(ctx,9), occupied = playerSlot(ctx,10), empty = playerSlot(ctx,35);
            // Interrupt a partially executed batch; no remaining forward placements may run.
            playerItem(srv,0,"minecraft:cobblestone",64); conn.waitForClientboundPackets(); ctx.waitTicks(2);
            ctx.runOnClient(mc -> StorageHelperClient.config().scrollClicksPerTick = 1);
            move(ctx,source); ctx.getInput().holdShift(); ctx.getInput().scroll(-3); ctx.waitTicks(3);
            int alreadyMoved = ctx.computeOnClient(mc -> {
                int n=0; for(int i=0;i<ChestSnapshots.chestSlotCount(mc.player.containerMenu);i++)
                    if(mc.player.containerMenu.getSlot(i).getItem().is(Items.COBBLESTONE)) n+=mc.player.containerMenu.getSlot(i).getItem().getCount();
                var session=ChestSessions.forScreen(mc.gui.screen());
                session.onScroll(mc.mouseHandler.getScaledXPos(mc.getWindow()),mc.mouseHandler.getScaledYPos(mc.getWindow()),1);
                return n-10;
            });
            check(alreadyMoved > 0 && alreadyMoved < 6,"test must interrupt a partially completed batch");
            ctx.waitTicks(15); ctx.getInput().releaseShift(); idle(ctx,conn);
            eq(10+Math.max(0,alreadyMoved-2),count(srv,pos,"minecraft:cobblestone"),"reverse must cancel unplaced work before returning completed items");
            ctx.runOnClient(mc -> StorageHelperClient.config().scrollClicksPerTick = 4);
            playerItem(srv,0,"minecraft:cobblestone",6); blockItem(srv,pos,0,"minecraft:cobblestone",10);
            conn.waitForClientboundPackets(); ctx.waitTicks(3);
            move(ctx,source); ctx.getInput().holdShift();
            // Two events in one client callback prove reversal before queued clicks execute.
            ctx.runOnClient(mc -> {
                var s = ChestSessions.forScreen(mc.gui.screen());
                double x=mc.mouseHandler.getScaledXPos(mc.getWindow()), y=mc.mouseHandler.getScaledYPos(mc.getWindow());
                s.onScroll(x,y,-3); s.onScroll(x,y,1);
            });
            ctx.waitTicks(8); ctx.getInput().releaseShift(); idle(ctx,conn);
            eq(10,count(srv,pos,"minecraft:cobblestone"),"queued deposit must be cancelled before execution");
            wheel(ctx,conn,source,-3); eq(16,count(srv,pos,"minecraft:cobblestone"),"deposit six");
            wheel(ctx,conn,occupied,1); eq(14,count(srv,pos,"minecraft:cobblestone"),"return two over unrelated occupied player slot");
            wheel(ctx,conn,empty,2); eq(10,count(srv,pos,"minecraft:cobblestone"),"return remaining four over empty player slot");
            wheel(ctx,conn,empty,2); eq(10,count(srv,pos,"minecraft:cobblestone"),"cannot return more than completed six");
            wheel(ctx,conn,source,-2); eq(14,count(srv,pos,"minecraft:cobblestone"),"repeat deposit four");
            var protectedBefore = protectedSlots(srv);
            var expectedMain = SortPlanner.computeTarget(main(srv),null,SortMode.ALPHABETICAL.comparator());
            ctx.clickScreenButton("storagehelper.button.player_sort"); idle(ctx,conn);
            same(protectedBefore,protectedSlots(srv),"player sort must not touch container/hotbar/equipment");
            check(SortPlanner.matches(main(srv),expectedMain),"player sort must conserve exact main-inventory totals/components");
            ctx.clickScreenButton("storagehelper.button.sort"); idle(ctx,conn);
            wheel(ctx,conn,empty,2); eq(10,count(srv,pos,"minecraft:cobblestone"),"return after both inventories were sorted");
            // Withdrawal history reverses from the container area, including empty/other slots.
            int donor = containerSlot(ctx,"minecraft:cobblestone");
            wheel(ctx,conn,donor,3); eq(4,count(srv,pos,"minecraft:cobblestone"),"withdraw six");
            wheel(ctx,conn,20,-1); eq(6,count(srv,pos,"minecraft:cobblestone"),"return two over empty container slot");
            wheel(ctx,conn,containerSlot(ctx,"minecraft:apple"),-2); eq(10,count(srv,pos,"minecraft:cobblestone"),"return withdrawal over different item");
            // Different identity replaces history; exact components are retained.
            playerItem(srv,5,"minecraft:cobblestone[custom_name='Marked']",3); conn.waitForClientboundPackets(); ctx.waitTicks(2);
            int named = playerSlot(ctx,14);
            wheel(ctx,conn,named,-1);
            wheel(ctx,conn,empty,1);
            eq(10,count(srv,pos,"minecraft:cobblestone"),"named items must not consume plain contents");
            eq(3,ctx.computeOnClient(mc -> mc.player.containerMenu.getSlot(named).getItem().getCount()),"named source restored");
            // Replace nonzero plain-item history with component-distinct history.
            wheel(ctx,conn,playerItemSlot(ctx,"minecraft:cobblestone"),-1);
            wheel(ctx,conn,named,-1); wheel(ctx,conn,empty,1);
            eq(12,count(srv,pos,"minecraft:cobblestone"),"only the new named transfer may be returned");
            wheel(ctx,conn,empty,1); eq(12,count(srv,pos,"minecraft:cobblestone"),"old plain history must not reappear");
            wheel(ctx,conn,containerSlot(ctx,"minecraft:cobblestone"),1);
            // External matching-item correction invalidates the remembered balance.
            int plain = playerItemSlot(ctx,"minecraft:cobblestone");
            wheel(ctx,conn,plain,-1);
            int corrected = count(srv,pos,"minecraft:cobblestone") + 1;
            blockItem(srv,pos,containerSlot(ctx,"minecraft:cobblestone"),"minecraft:cobblestone",corrected);
            conn.waitForClientboundPackets(); ctx.waitTicks(3);
            wheel(ctx,conn,empty,1); eq(corrected,count(srv,pos,"minecraft:cobblestone"),"external changes must invalidate reversal");
            // A fully deposited unstackable has an empty origin. Fill every return slot.
            playerItem(srv,0,"minecraft:diamond_sword[custom_name='Undo']",1); conn.waitForClientboundPackets(); ctx.waitTicks(2);
            wheel(ctx,conn,playerSlot(ctx,9),-1);
            for (int i=0;i<27;i++) playerItem(srv,i,"minecraft:stone",64);
            for (int i=0;i<9;i++) srv.runCommand("item replace entity @a hotbar."+i+" with minecraft:stone 64");
            conn.waitForClientboundPackets(); ctx.waitTicks(2);
            wheel(ctx,conn,playerSlot(ctx,10),1); eq(1,count(srv,pos,"minecraft:diamond_sword"),"full return destination must stop safely");
            playerItem(srv,1,"minecraft:air",1); conn.waitForClientboundPackets(); ctx.waitTicks(2);
            wheel(ctx,conn,playerSlot(ctx,10),1); eq(0,count(srv,pos,"minecraft:diamond_sword"),"return to eligible empty slot when original is full");
            wheel(ctx,conn,playerSlot(ctx,10),-1); eq(1,count(srv,pos,"minecraft:diamond_sword"),"deposit for close test");
            close(ctx); open(ctx,conn,pos,block.contains("shulker"));
            wheel(ctx,conn,playerSlot(ctx,10),1); eq(1,count(srv,pos,"minecraft:diamond_sword"),"closing must clear history");
            close(ctx);
        }
        // Survival inventory menu: exact backing indexes protect all equipment/crafting slots.
        srv.runCommand("gamemode survival @a"); srv.runCommand("clear @a");
        playerItem(srv,0,"minecraft:stone",20); playerItem(srv,3,"minecraft:apple",3);
        playerItem(srv,5,"minecraft:stone",44); playerItem(srv,8,"minecraft:diamond_sword",1);
        srv.runCommand("item replace entity @a hotbar.0 with minecraft:dirt 7");
        srv.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
        srv.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
        srv.computeOnServer(s -> { var p=s.getPlayerList().getPlayers().getFirst(); p.inventoryMenu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS,1)); p.inventoryMenu.broadcastChanges(); return true; });
        conn.waitForClientboundPackets(); ctx.waitTicks(3);
        ctx.runOnClient(mc -> StorageHelperClient.config().setSortMode(SortMode.ALPHABETICAL));
        ctx.getInput().pressKey(o -> o.keyInventory); ctx.waitForScreen(InventoryScreen.class);
        for (SortMode mode : SortMode.values()) {
            if (mode == SortMode.BY_TYPE) ctx.clickScreenButton("Order: A-Z");
            var protectedBefore = protectedSlots(srv);
            var expectedMain = SortPlanner.computeTarget(main(srv),null,mode.comparator());
            ctx.clickScreenButton("storagehelper.button.player_sort"); idle(ctx,conn);
            same(protectedBefore,protectedSlots(srv),"survival sorting changed protected slots");
            var actual = main(srv);
            check(SortPlanner.matches(actual,expectedMain),"server player main inventory must match selected order");
            ctx.runOnClient(mc -> mc.gui.toastManager().clear()); ctx.waitTick(); ctx.takeScreenshot("storagehelper-player-sort-"+mode.id());
        }
        close(ctx);
    }
    private static String xyz(BlockPos p) { return p.getX()+" "+p.getY()+" "+p.getZ(); }
    private static void blockItem(TestServerContext s,BlockPos p,int slot,String item,int n) { s.runCommand("item replace block "+xyz(p)+" container."+slot+" with "+item+" "+n); }
    private static void playerItem(TestServerContext s,int i,String item,int n) { s.runCommand("item replace entity @a inventory."+i+" with "+item+" "+n); }
    private static void check(boolean v,String message) { if(!v) throw new AssertionError(message); }
    private static void eq(int expected,int actual,String message) { check(expected==actual,message+": "+actual+" expected "+expected); }
    private static int count(TestServerContext s,BlockPos p,String id) {
        return s.computeOnServer(server -> { var c=(Container)server.overworld().getBlockEntity(p); int n=0; for(int i=0;i<c.getContainerSize();i++) if(!c.getItem(i).isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.getItem(i).getItem()).toString().equals(id)) n+=c.getItem(i).getCount(); return n; });
    }
    private static void open(ClientGameTestContext c,TestServerConnection conn,BlockPos p,boolean shulker) {
        conn.waitForClientboundPackets(); c.getInput().lookAt(p); c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        if(shulker)c.waitForScreen(ShulkerBoxScreen.class);else c.waitForScreen(ContainerScreen.class);
        c.waitTicks(2);
    }
    private static void close(ClientGameTestContext c) { c.getInput().pressKey(InputConstants.KEY_ESCAPE); c.waitForScreen(null); }
    private static int playerSlot(ClientGameTestContext c,int backing) { return c.computeOnClient(mc -> mc.player.containerMenu.slots.stream().filter(s -> s.container==mc.player.getInventory() && s.getContainerSlot()==backing).findFirst().orElseThrow().index); }
    private static int containerSlot(ClientGameTestContext c,String id) { return c.computeOnClient(mc -> java.util.stream.IntStream.range(0,ChestSnapshots.chestSlotCount(mc.player.containerMenu)).filter(i -> !mc.player.containerMenu.getSlot(i).getItem().isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.containerMenu.getSlot(i).getItem().getItem()).toString().equals(id)).findFirst().orElseThrow()); }
    private static int playerItemSlot(ClientGameTestContext c,String id) { return c.computeOnClient(mc -> mc.player.containerMenu.slots.stream().filter(s -> s.container==mc.player.getInventory() && !s.getItem().isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString().equals(id) && !s.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)).findFirst().orElseThrow().index); }
    private static void move(ClientGameTestContext c,int index) {
        double[] xy=c.computeOnClient(mc -> { var screen=(AbstractContainerScreen<?>)mc.gui.screen(); var a=(AbstractContainerScreenAccessor)screen; var slot=screen.getMenu().getSlot(index); double u=mc.getWindow().getGuiScale(); return new double[]{(a.storagehelper$getLeftPos()+slot.x+8)*u,(a.storagehelper$getTopPos()+slot.y+8)*u}; });
        c.getInput().setCursorPos(xy[0],xy[1]);c.waitTick();
    }
    private static void wheel(ClientGameTestContext c,TestServerConnection conn,int slot,double delta) {
        move(c,slot); c.getInput().holdShift(); c.getInput().scroll(delta); c.waitTicks(10); c.getInput().releaseShift(); idle(c,conn);
    }
    private static void idle(ClientGameTestContext c,TestServerConnection conn) {
        c.waitTicks(2); c.waitFor(mc -> ChestSessions.forScreen(mc.gui.screen()) == null || !ChestSessions.forScreen(mc.gui.screen()).isOperationRunning(),200);
        conn.waitForServerboundPackets();conn.waitForClientboundPackets();
        check(c.computeOnClient(mc -> mc.player.containerMenu.getCarried().isEmpty()),"operation left a cursor stack");
    }
    private static java.util.List<com.storagehelper.logic.Stack> main(TestServerContext s) {
        return s.computeOnServer(server -> { var p=server.getPlayerList().getPlayers().getFirst(); return InventoryRegion.playerMain(p.containerMenu,p.getInventory()).snapshot(); });
    }
    private static List<ItemStack> protectedSlots(TestServerContext s) {
        return s.computeOnServer(server -> { var p=server.getPlayerList().getPlayers().getFirst(); var region=InventoryRegion.playerMain(p.containerMenu,p.getInventory()); return p.containerMenu.slots.stream().filter(slot -> !region.slots().contains(slot.index)).map(slot -> slot.getItem().copy()).toList(); });
    }
    private static void same(List<ItemStack> before,List<ItemStack> after,String reason) { eq(before.size(),after.size(),reason);for(int i=0;i<before.size();i++)check(ItemStack.matches(before.get(i),after.get(i)),reason+" slot "+i); }
}

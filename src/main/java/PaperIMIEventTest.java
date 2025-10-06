import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class PaperIMIEventTest extends JavaPlugin {
  public static JavaPlugin plugin;
  public PaperIMIEventTest(){plugin = this;}

  // StorageSign の張り付けられる面一覧
  public static final BlockFace[] faceList = {BlockFace.UP, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST};

  @Override
  public void onEnable() {
    // イベントサンプル
    getServer().getPluginManager().registerEvents(new testEvent(),this);
  }

  public class testEvent implements Listener{
    @EventHandler
    public void onEvent(InventoryMoveItemEvent event){
      getLogger().info("<< EVENT TEST LOG START >>");
      // 受取先インベントリ
      Inventory destination = event.getDestination();
      // 送り元のインベントリ
      Inventory eventSource = event.getSource();
      // 転送開始インベントリ
      Inventory initiator = event.getInitiator();

      // 移動するアイテムのクローン
      ItemStack moveItem = event.getItem().clone();

      // 移動するInventoryに 最大スタック + moveItem 以上のアイテムがある場合は処理しない
      //int i = moveItem.getMaxStackSize() + moveItem.getAmount();
      //if (eventSource.containsAtLeast(moveItem, i)) return;
      int i = moveItem.getMaxStackSize();
      getLogger().info("moveEvent:CheckStackAmount" + i);

      // 送り元のブロック情報
      List<Block> souBlock;
      if (eventSource instanceof DoubleChestInventory doubleChest){
        // ダブルチェストの場合 左右を登録
        souBlock = new ArrayList<>(Arrays.asList(
            ((Container) doubleChest.getLeftSide().getHolder()).getBlock(),
            ((Container) doubleChest.getRightSide().getHolder()).getBlock()
        ));
      } else if (eventSource.getHolder() instanceof Container souContainer){
        // コンテナーなら 一つ登録
        souBlock = List.of(souContainer.getBlock());
      } else {
        // その他は処理しない
        return;
      }

      // 送り元ブロックからSS情報を取得
      List<Block> targetSS = new ArrayList<>();
      for (Block block : souBlock){
        for (BlockFace face: faceList) {
          // SSなのかチェックするブロック
          Block check = block.getRelative(face);
          BlockState blockState = check.getState();

          if (blockState instanceof Sign sign){
            // Sign チェック
            SignSide signSide = sign.getSide(Side.FRONT);
            // StorageSign チェック
            List<Component> componentList = signSide.lines();
            Component component = signSide.line(0);
            String s1 = signSide.getLine(0);
            String s2 = signSide.line(0).insertion();
            if (!signSide.getLine(0).equals("StorageSign")) continue;
            // SS収納アイテム名
            String SSItem = signSide.getLine(1);
            // SS収納アイテム数
            int amount = Integer.parseInt(signSide.getLine(2));
            // アイテム数が0だったら処理しない
            if (amount == 0) continue;

            Material material = check.getType();
            String itemString = moveItem.getType().toString();
            if (SSItem.equals(itemString)) {
              // SSのアイテム名と移動させるアイテム名が同じなら
              if (material.data.equals(org.bukkit.block.data.type.Sign.class)) {
                // 上から刺してる Sign
                if (check.getRelative(BlockFace.DOWN).equals(block)) {
                  // 位置関係が 上 Sign 下 Container ならアイテムを出す
                  targetSS.add(check);
                }
              } else if (material.data.equals(WallSign.class)) {
                // 横から張り付けてる Sign
                WallSign wallSign = (WallSign) check.getBlockData();
                if (check.getRelative(wallSign.getFacing().getOppositeFace()).equals(block)) {
                  // Container に張り付けてある ならアイテムを出す
                  targetSS.add(check);
                }
              }
            }
          }
        }
      }

      // アイテムと一致するSSが無いなら処理しない
      if (targetSS.isEmpty()) return;

      /*
          タスク登録
          最初に見つけたSSを登録
      */
      Block block = targetSS.getFirst();
      Sign side = (Sign) block.getState();
      new testTask(initiator, moveItem, side).runTask(plugin);

      getLogger().info("<< EVENT TEST LOG END >>");
      return;
    }
  }

  /**
   * ホッパー処理用タスク
   */
  public static class testTask extends BukkitRunnable {

    // 確認する Inventory
    Inventory initiator;
    // 動くはずの ItemStack
    ItemStack moveItem;
    // チェックをする SS
    Sign SS;

    /**
     * ホッパー処理タスク
     * @param initiatorInventory チェックするInventory
     * @param moveItemStack 動くはずのItemStackデータ
     * @param StorageSign 搬出用SS
     */
    testTask(Inventory initiatorInventory, ItemStack moveItemStack, Sign StorageSign){
      initiator = initiatorInventory;
      moveItem = moveItemStack;
      SS = StorageSign;
    }

    @Override
    public void run() {
      plugin.getLogger().info("<< TASK TEST LOG START >>");

      plugin.getLogger().info("taskEvent:moveItem: " + moveItem.getType());

      boolean flag = initiator.containsAtLeast(moveItem, moveItem.getMaxStackSize());
      plugin.getLogger().info("taskEvent:initiatorCheck: " + flag );
      // アイテムが移動成功していたら MaxStackSize 以下になるので補充
      if(flag) return;
      
      // SS内のAmount数を変更
      SignSide SSSide = SS.getSide(Side.FRONT);
      int SSAmount = Integer.parseInt(SSSide.getLine(2));
      plugin.getLogger().info("taskEvent:SSAmount:Before " + SSAmount);

      SSAmount = SSAmount - moveItem.getAmount();
      SSSide.setLine(2,String.valueOf(SSAmount));
      SS.update();

      plugin.getLogger().info("taskEvent:SSAmount:After " + SSAmount);

      // アイテムの補充
      initiator.addItem(moveItem);

      plugin.getLogger().info("<< TASK TEST LOG END >>");
    }
  }
}

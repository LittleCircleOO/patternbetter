package com.xiaopiao.patternbetter.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.glodblock.github.glodium.network.packet.sync.ActionMap;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.*;

@Mixin(value =  PatternProviderMenu.class)
@Implements(@Interface(iface = IActionHolder.class, prefix = "IActionHolder$"))
public class PatternProviderMenuMixin extends AEBaseMenu {

    @Unique
    private ActionMap actionMap = ActionMap.create();

    @Unique
    ContainerExPatternProvider provider;

    @Unique
    private int playerSlotCount;


    public PatternProviderMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"),remap = false)
    public void init(MenuType menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci){
        List<Slot> playerSlots = (this).getSlots(SlotSemantics.PLAYER_INVENTORY);
        this.playerSlotCount = playerSlots.size();

        this.actionMap.put("multiply2", (paras) -> multiply2(false,2));
        this.actionMap.put("divide2", (paras) -> multiply2(true ,2));
        this.actionMap.put("multiply5", (paras) -> multiply2(false,5));
        this.actionMap.put("divide5", (paras) -> multiply2(true , 5));
        this.actionMap.put("multiply10", (paras) -> multiply2(false , 10));
        this.actionMap.put("divide10", (paras) -> multiply2(true , 10));
        this.actionMap.put("patternsInto" ,(paras) -> patternsInto());
        this.actionMap.put("balance", (paras) -> balanceMultiply());


        if ((PatternProviderMenu)(Object)this instanceof ContainerExPatternProvider p){
            provider = p;
        }

    }

    @Unique
    public boolean getEdit(int slotIndex) {
        try {
            if (provider == null)throw new RuntimeException("ContainerExPatternProvider is null");

            Class<? extends ContainerExPatternProvider> aClass = provider.getClass();

            Field field = aClass.getDeclaredField("allSlotStates");

            BitSet o = (BitSet)field.get(provider);

            return o.get(slotIndex - 36);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }


    @Unique
    public void patternsInto(){
        List<Slot> playerSlots = this.getSlots(SlotSemantics.PLAYER_INVENTORY);
        List<Slot> patternSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN);

        // 1. 遍历玩家背包，收集所有样板
        List<ItemStack> patternsToMove = new ArrayList<>();
        for (Slot slot : playerSlots) {
            ItemStack item = slot.getItem();
            if (PatternDetailsHelper.isEncodedPattern(item)) {
                patternsToMove.add(item.copy()); // 避免直接修改原物品
                slot.set(ItemStack.EMPTY); // 清空玩家背包中的样板
            }
        }

        // 2. 将样板填充到供应器的空位
        int patternIndex = 0;
        for (Slot patternSlot : patternSlots) {
            if (patternSlot.getItem().isEmpty() && patternIndex < patternsToMove.size()) {
                patternSlot.set(patternsToMove.get(patternIndex));
                patternIndex++;
            }
        }

    }

    @Unique
    public void balanceMultiply(){
        List<Slot> slots = (this).getSlots(SlotSemantics.ENCODED_PATTERN);

        List<Long> GBCs = new ArrayList<>();

        for (Slot slot : slots){
            ItemStack stack = slot.getItem();
            IPatternDetails detail = PatternDetailsHelper.decodePattern(stack, (this).getPlayer().level());
            if (detail instanceof AEProcessingPattern process){
                GenericStack[] input = (GenericStack[])process.getSparseInputs().toArray(new GenericStack[0]);
                GenericStack[] output = (GenericStack[])process.getOutputs().toArray(new GenericStack[0]);
                List<GenericStack> inputs = new ArrayList<>();
                //将数组内容添加
                inputs.addAll(Arrays.asList(input));
                inputs.addAll(Arrays.asList(output));

                //过滤数量小于1的并转化为long[]
                long[] inputAmount = inputs.stream().filter(Objects::nonNull).filter(stack1 -> stack1.amount() > 0).mapToLong(GenericStack::amount).toArray();

                long l = gcdOfArray(inputAmount);

                //所有同除最大公约数 stream
                GenericStack[] mulInput = Arrays.stream(input).filter(Objects::nonNull).map(stack1 -> new GenericStack(stack1.what(), stack1.amount() / l)).toArray(GenericStack[]::new);
                GenericStack[] mulOutput = Arrays.stream(output).filter(Objects::nonNull).map(stack1 -> new GenericStack(stack1.what(), stack1.amount() / l)).toArray(GenericStack[]::new);

                ItemStack newPattern = PatternDetailsHelper.encodeProcessingPattern(Arrays.stream(mulInput).toList(), Arrays.stream(mulOutput).toList());
                slot.set(newPattern);
                GBCs.add(l);
            }
        }

        //寻找最大的数
        long maxGCD = GBCs.stream().max(Long::compareTo).orElse(1L);

        multiply2(false, (int) maxGCD);
    }

    // 计算两个数的最大公约数（GCD）
    @Unique
    public long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }
    // 计算 N 个数的 GCD
    @Unique
    public long gcdOfArray(long[] numbers) {
        if (numbers.length == 0) return 1;
        long currentGcd = numbers[0];
        for (long i = 1; i < numbers.length; i++) {
            currentGcd = gcd(currentGcd, numbers[Math.toIntExact(i)]);
            if (currentGcd == 1) break; // 提前终止，因为 GCD 不可能小于 1
        }
        return currentGcd;
    }

    @Unique
    public void multiply2(boolean is , int i){
        List<Slot> slots = (this).getSlots(SlotSemantics.ENCODED_PATTERN);
        for (Slot slot : slots) {
            if (provider == null){
                ItemStack stack = slot.getItem();
                IPatternDetails detail = PatternDetailsHelper.decodePattern(stack, (this).getPlayer().level());
                if (detail instanceof AEProcessingPattern process) {
                    GenericStack[] input = (GenericStack[])process.getSparseInputs().toArray(new GenericStack[0]);
                    GenericStack[] output = (GenericStack[])process.getOutputs().toArray(new GenericStack[0]);
                    GenericStack[] mulInput = new GenericStack[input.length];
                    GenericStack[] mulOutput = new GenericStack[output.length];


                    if ((hasStackWithCountOne( input , i) || hasStackWithCountOne(output, i)) && is)continue;

                    modifyStacks(input,  mulInput, i, is);
                    modifyStacks(output, mulOutput, i, is);

                    ItemStack newPattern = PatternDetailsHelper.encodeProcessingPattern(Arrays.stream(mulInput).toList(), Arrays.stream(mulOutput).toList());
                    slot.set(newPattern);

                }
            }else if (getEdit(slot.index)){
                ItemStack stack = slot.getItem();
                IPatternDetails detail = PatternDetailsHelper.decodePattern(stack, (this).getPlayer().level());
                if (detail instanceof AEProcessingPattern process) {
                    GenericStack[] input = (GenericStack[])process.getSparseInputs().toArray(new GenericStack[0]);
                    GenericStack[] output = (GenericStack[])process.getOutputs().toArray(new GenericStack[0]);
                    GenericStack[] mulInput = new GenericStack[input.length];
                    GenericStack[] mulOutput = new GenericStack[output.length];


                    if ((hasStackWithCountOne( input , i) || hasStackWithCountOne(output, i)) && is)continue;

                    modifyStacks(input,  mulInput, i, is);
                    modifyStacks(output, mulOutput, i, is);

                    ItemStack newPattern = PatternDetailsHelper.encodeProcessingPattern(Arrays.stream(mulInput).toList(), Arrays.stream(mulOutput).toList());
                    slot.set(newPattern);

                }
            }


        }
    }


    @Unique
    private void modifyStacks(GenericStack[] input, GenericStack[] mulInput,int scale, boolean div) {
        for(int i = 0; i < input.length; ++i) {
            if (input[i] != null ) {
                long amt = div ? input[i].amount() / (long)scale : input[i].amount() * (long)scale;
                mulInput[i] = new GenericStack(input[i].what(), amt);
            }
        }
    }
    @Unique
    private static boolean hasStackWithCountOne(GenericStack[] stacks , int i) {
        for (GenericStack stack : stacks) {
            if (stack != null && (stack.amount() % i != 0  || stack.amount() /i <0)) {
                return true;
            }
        }
        return false;
    }
    @Unique
    public @NotNull ActionMap IActionHolder$getActionMap(){
        return this.actionMap;
    }


}

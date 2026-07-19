package dev.isaac.fallow.mixin;

import dev.isaac.fallow.diet.DietService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the moment a player finishes eating ({@code completeUsingItem}) to record the
 * consumed item in the diet window. The eaten stack is read before vanilla consumes it (HEAD
 * injection), and the call is forwarded only for server-side players.
 *
 * <p>No nested types: hard rule for Mixin classes in this codebase (nested types crash at link
 * time under 26.1).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEatMixin {

    @Inject(method = "completeUsingItem()V", at = @At("HEAD"))
    private void fallow$recordMeal(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (!(self instanceof ServerPlayer player)) {
            return;
        }
        if (!self.isUsingItem()) {
            return;
        }
        ItemStack eaten = self.getUseItem();
        if (eaten.isEmpty()) {
            return;
        }
        // Mirror vanilla's abort guard: completeUsingItem re-checks useItem against the held
        // stack and releases (without eating) on a mismatch, so an aborted eat must not credit
        // a meal.
        if (!eaten.equals(self.getItemInHand(self.getUsedItemHand()))) {
            return;
        }
        DietService.recordMeal(player, eaten);
    }
}

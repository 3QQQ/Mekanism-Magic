package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import org.jetbrains.annotations.Nullable;

/** Installs real Mekanism FLUID side configuration over existing essence banks. */
public final class EssenceFluidPorts {
    private EssenceFluidPorts() { }

    public static boolean available() {
        return EssenceFluidCodec.available();
    }

    @Nullable
    public static <T extends NativeMagicMachineBlockEntity & EssenceFluidBankHost>
    IFluidTankHolder input(T tile, IContentsListener listener, int... channels) {
        return install(tile, listener, true, false, channels);
    }

    @Nullable
    public static <T extends NativeMagicMachineBlockEntity & EssenceFluidBankHost>
    IFluidTankHolder output(T tile, int channel, IContentsListener listener) {
        return install(tile, listener, false, true, channel);
    }

    /** A unified generator may expose all four existing scalar banks as output-only tanks. */
    @Nullable
    public static <T extends NativeMagicMachineBlockEntity & EssenceFluidBankHost>
    IFluidTankHolder output(T tile, IContentsListener listener, int... channels) {
        return install(tile, listener, false, true, channels);
    }

    @Nullable
    private static <T extends NativeMagicMachineBlockEntity & EssenceFluidBankHost>
    IFluidTankHolder install(T tile, IContentsListener listener,
                             boolean input, boolean output, int... channels) {
        if (!available() || channels.length == 0) return null;
        ConfigInfo config = tile.getConfig().getConfig(TransmissionType.FLUID);
        if (config == null) return null;
        List<IExtendedFluidTank> tanks = new ArrayList<>(channels.length);
        FluidTankHelper holder = FluidTankHelper.forSideWithConfig(tile);
        for (int channel : channels) {
            IExtendedFluidTank tank = new EssenceBankFluidTank(tile, channel,
                    input, output, listener);
            tanks.add(tank);
            holder.addTank(tank);
        }
        DataType mode = input ? DataType.INPUT : DataType.OUTPUT;
        config.addSlotInfo(mode, new FluidSlotInfo(input, output, tanks));
        // This is the FLUID config only; the existing ITEM eject setting is
        // untouched. Producers may opt into Mek's fluid ejector separately.
        config.setCanEject(output);
        config.setEjecting(output);
        for (RelativeSide side : RelativeSide.values()) {
            config.setDataType(DataType.NONE, side);
        }
        config.setDataType(mode, RelativeSide.TOP);
        return holder.build();
    }
}

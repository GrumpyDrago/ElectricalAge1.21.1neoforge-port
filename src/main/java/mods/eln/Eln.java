package mods.eln;

import mods.eln.block.ArcClayBlock;
import mods.eln.block.ArcMetalBlock;
import mods.eln.client.ClientProxy;
import mods.eln.compat.OreDict;
import mods.eln.craft.RecipeInjector;
import mods.eln.datagen.ElnDataGenerator;
import mods.eln.entity.ElnEntities;
import mods.eln.gametest.ClientSmokeTest;
import mods.eln.generic.DescriptorCollection;
import mods.eln.integration.cc.ComputerCraftIntegration;
import mods.eln.ore.ConfigPlacement;
import mods.eln.packets.ElnPayload;
import mods.eln.registration.ElnBlockEntities;
import mods.eln.registration.ElnRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import mods.eln.compat.Mc;

import net.neoforged.bus.api.SubscribeEvent;
import mods.eln.compat.Side;
import mods.eln.cable.CableRenderDescriptor;
import mods.eln.client.ClientKeyHandler;
import mods.eln.config.JsonConfig;
import mods.eln.craft.CraftingRecipes;
import mods.eln.entity.ReplicatorPopProcess;
import mods.eln.eventhandlers.RoomThermalBlockEventsHandler;
import mods.eln.fluid.FuelRegistry;
import mods.eln.fluid.ThermalRegistry;
import mods.eln.fluid.FluidRegistrationKt;
import mods.eln.environment.BiomeClimateService;
import mods.eln.generic.GenericCreativeTab;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.generic.GenericItemUsingDamageDescriptorWithComment;
import mods.eln.generic.SharedItem;
import mods.eln.ghost.GhostBlock;
import mods.eln.ghost.GhostManager;
import mods.eln.item.*;
import mods.eln.item.electricalinterface.ItemEnergyInventoryProcess;
import mods.eln.item.lampitem.LampLists;
import mods.eln.lightblock.LightBlock;
import mods.eln.lightblock.LightBlockEntity;
import mods.eln.misc.*;
import mods.eln.mqtt.MqttManager;
import mods.eln.metrics.MetricsSubsystem;
import mods.eln.node.NodeBlockEntity;
import mods.eln.node.NodeManager;
import mods.eln.node.NodeServer;
import mods.eln.node.six.*;
import mods.eln.node.transparent.*;
import mods.eln.ore.OreBlock;
import mods.eln.ore.OreDescriptor;
import mods.eln.ore.OreItem;
import mods.eln.ore.OreScannerManager;
import mods.eln.packets.*;
import mods.eln.registration.ItemRegistration;
import mods.eln.registration.SingleNodeRegistration;
import mods.eln.registration.SixNodeRegistration;
import mods.eln.registration.TransparentNodeRegistration;
import mods.eln.railroad.ElectricMinecartChargeReporter;
import mods.eln.server.*;
import mods.eln.server.console.ElnConsoleCommands;
import mods.eln.sim.Simulator;
import mods.eln.sim.ThermalLoadInitializer;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.nbt.NbtElectricalLoad;
import mods.eln.simplenode.computerprobe.ComputerProbeBlock;
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherBlock;
import mods.eln.sixnode.PortableNaNDescriptor;
import mods.eln.sixnode.currentcable.CurrentCableDescriptor;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import mods.eln.sixnode.electricaldatalogger.DataLogsPrintDescriptor;
import mods.eln.sixnode.lampsupply.LampSupplyElement;
import mods.eln.sixnode.modbusrtu.ModbusTcpServer;
import mods.eln.sixnode.tutorialsign.TutorialSignElement;
import mods.eln.sixnode.wirelesssignal.IWirelessSignalSpot;
import mods.eln.sixnode.wirelesssignal.tx.WirelessSignalTxElement;
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceDescriptor;
import mods.eln.transparentnode.teleporter.TeleporterElement;
import net.minecraft.world.level.block.Block;
import mods.eln.generic.GenericCreativeTab;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.level.material.Fluid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;
import java.util.*;

import static mods.eln.i18n.I18N.TR;
import static mods.eln.i18n.I18N.TR_GROUP;
import static mods.eln.i18n.I18N.tr;

@Mod(Eln.MODID)
public class Eln {
    public static Eln instance;
    public final static String MODID = "eln";
    public final static Logger LOGGER = LogManager.getLogger("Eln");
    public final static String NAME = "Electrical Age - jrddunbr's build";
    public final static String UPDATE_URL = "https://github.com/age-series/ElectricalAge/releases";
    public final static String[] AUTHORS = {"Dolu1990", "jrddunbr", "Baughn", "Grissess", "Caeleron", "Omega_Haxors",
     "lambdaShade", "cm0x4D", "metc", "TheBuilderBoy76", "Justus0405"};
    public static final String channelName = "miaouMod";
    public static final double solarPanelBasePower = 65.0;
    public static final byte packetPlayerKey = 14;
    public static final byte packetNodeSingleSerialized = 15;
    public static final byte packetPublishForNode = 16;
    public static final byte packetOpenLocalGui = 17;
    public static final byte packetForClientNode = 18;
    public static final byte packetPlaySound = 19;
    public static final byte packetDestroyUuid = 20;
    public static final byte packetClientToServerConnection = 21;
    public static final byte packetServerToClientInfo = 22;
    public static final byte packetFalstadImport = 23;
    public static final Obj3DFolder obj = new Obj3DFolder();
    public static final double gateInputCurrent = 0.00005;
    public static final double gateOutputCurrent = 0.100;
    public static final double LVU = 50;
    public static final double MVU = 200;
    public static final double HVU = 800;
    public static final double VVU = 3200;
    public static final double CCU = 120_000;
    public static final double cableHeatingTime = 30;
    public static final double cableWarmLimit = 130;
    public static final double cableThermalConductionTao = 0.5;
    public static final ThermalLoadInitializer cableThermalLoadInitializer =
     new ThermalLoadInitializer(cableWarmLimit, -100, cableHeatingTime, cableThermalConductionTao);
    public static final ThermalLoadInitializer sixNodeThermalLoadInitializer =
     new ThermalLoadInitializer(cableWarmLimit, -100, cableHeatingTime, 1000);
    public static final HashMap<String, ItemStack> dictionnaryOreFromMod = new HashMap<>();
    public static Logger logger = LogManager.getLogger("ELN");
    public static JsonConfig config = new JsonConfig(new File("config/Eln.cfg"));
    public static PacketHandler packetHandler;
    public static LiveDataManager clientLiveDataManager;
    public static ClientKeyHandler clientKeyHandler;
    public static SaveConfig saveConfig;
    public static GhostManager ghostManager;
    public static ModbusTcpServer modbusServer;
    public static Simulator simulator = null;
    public static DelayedTaskManager delayedTask;
    public static ItemEnergyInventoryProcess itemEnergyInventoryProcess;
    public static GenericCreativeTab creativeTab;
    public static GenericCreativeTab creativeTabPowerElectronics;
    public static GenericCreativeTab creativeTabSignalProcessing;
    public static GenericCreativeTab creativeTabLighting;
    public static GenericCreativeTab creativeTabCables;
    public static GenericCreativeTab creativeTabPowerDistribution;
    public static GenericCreativeTab creativeTabToolsArmor;
    public static GenericCreativeTab creativeTabOresMaterials;
    public static GenericCreativeTab creativeTabMachines;
    public static GenericCreativeTab creativeTabCreative;
    public static GenericCreativeTab creativeTabOther;
    public static Item swordCopper, hoeCopper, shovelCopper, pickaxeCopper, axeCopper;
    public static GenericItemUsingDamageDescriptorWithComment plateCopper;
    public static ArmorItem helmetCopper, chestplateCopper, legsCopper, bootsCopper;
    public static ArmorItem helmetECoal, plateECoal, legsECoal, bootsECoal;
    public static SharedItem sharedItem;
    public static SharedItem sharedItemStackOne;
    public static ItemStack wrenchItemStack;
    public static SixNodeBlock sixNodeBlock;
    public static TransparentNodeBlock transparentNodeBlock;
    public static GhostBlock ghostBlock;
    public static LightBlock lightBlock;
    public static ArcClayBlock arcClayBlock;
    public static ArcMetalBlock arcMetalBlock;
    public static SixNodeItem sixNodeItem;
    public static TransparentNodeItem transparentNodeItem;
    public static OreItem oreItem;
    public static PortableNaNDescriptor portableNaNDescriptor = null;
    public static CableRenderDescriptor stdPortableNaN = null;
    public static boolean mqttEnabled = false;
    public static boolean simMetricsEnabled = false;
    public static String simMetricsMqttServer = "";
    public static String simMetricsId = "server";
    public static int simMetricsPublishIntervalTicks = 20;
    public static boolean debugEnabled = false;
    public static SiliconWafer siliconWafer;
    public static Transistor transistor;
    public static Thermistor thermistor;
    public static NibbleMemory nibbleMemory;
    public static ArithmeticLogicUnit alu;
    public static String dictSiliconWafer;
    public static String dictTransistor;
    public static String dictThermistor;
    public static String dictNibbleMemory;
    public static String dictALU;
    public static WindProcess wind;
    static public GenericItemUsingDamageDescriptor multiMeterElement, thermometerElement, allMeterElement;
    static public GenericItemUsingDamageDescriptor configCopyToolElement;
    static public GenericItemUsingDamageDescriptor falstadImportToolElement;
    public static TreeResin treeResin;
    public static MiningPipeDescriptor miningPipeDescriptor;
    static NodeServer nodeServer;
    private static NodeManager nodeManager;
    public static OreDescriptor oreCopper;
    public static GenericItemUsingDamageDescriptorWithComment dustCopper;
    public ArrayList<IConfigSharing> configShared = new ArrayList<>();
    public CopperCableDescriptor copperCableDescriptor;
    public WireScrapDescriptor wireScrapDescriptor;
    public WoundWireBundleDescriptor woundWireBundleDescriptor;
    public ElectricalCableDescriptor creativeCableDescriptor;
    public ElectricalCableDescriptor veryHighVoltageCableDescriptor;
    public ElectricalCableDescriptor highVoltageCableDescriptor;
    public ElectricalCableDescriptor signalCableDescriptor;
    public ElectricalCableDescriptor lowVoltageCableDescriptor;
    public ElectricalCableDescriptor meduimVoltageCableDescriptor;
    public ElectricalCableDescriptor signalBusCableDescriptor;
    public CurrentCableDescriptor lowCurrentCableDescriptor;
    public CurrentCableDescriptor mediumCurrentCableDescriptor;
    public CurrentCableDescriptor highCurrentCableDescriptor;
    public Double ELN_CONVERTER_MAX_POWER = 120_000.0;
    public ServerEventListener serverEventListener;
    public CableRenderDescriptor stdCableRenderSignal;
    public CableRenderDescriptor stdCableRenderSignalBus;
    public CableRenderDescriptor stdCableRender50V;
    public CableRenderDescriptor stdCableRender200V;
    public CableRenderDescriptor stdCableRender800V;
    public CableRenderDescriptor stdCableRender3200V;
    public CableRenderDescriptor stdCableRenderCreative;
    public CableRenderDescriptor lowCurrentCableRender;
    public CableRenderDescriptor mediumCurrentCableRender;
    public CableRenderDescriptor highCurrentCableRender;
    public FunctionTable batteryVoltageFunctionTable;
    public ArrayList<ItemStack> furnaceList = new ArrayList<ItemStack>();
    public RecipesList maceratorRecipes = new RecipesList();
    public RecipesList compressorRecipes = new RecipesList();
    public RecipesList plateMachineRecipes = new RecipesList();
    public RecipesList arcFurnaceRecipes = new RecipesList();
    public RecipesList magnetiserRecipes = new RecipesList();
    public GenericItemUsingDamageDescriptorWithComment copperIngot, plumbIngot, tungstenIngot;
    public DataLogsPrintDescriptor dataLogsPrintDescriptor;
    public static final double SVU = 5;
    public static final double signalVoltageAcceptNegative = -0.5;
    public static final double signalVoltageAcceptPositive = SVU + 0.5;
    public static final double SVII = gateInputCurrent / SVU, SVUinv = 1.0 / SVU;
    public EnergyConverterElnToOtherBlock elnToOtherBlockConverter;
    public ComputerProbeBlock computerProbeBlock;
    public static final double SVP = gateOutputCurrent * SVU;
    public ElectricalFurnaceDescriptor electricalFurnace;

    public static BrushDescriptor whiteDesc;

    public static List<String> brushSubNames;

    public static double getSmallRs() {
        return instance.lowVoltageCableDescriptor.electricalRs;
    }

    public static void applySmallRs(NbtElectricalLoad aLoad) {
        instance.lowVoltageCableDescriptor.applyTo(aLoad);
    }

    public static void applySmallRs(Resistor r) {
        instance.lowVoltageCableDescriptor.applyTo(r);
    }

    public static ItemStack findItemStack(String name, int stackSize) {
        ItemStack stack = ElnRegistry.findStack(name, stackSize);
        if (stack != null) return stack;
        stack = dictionnaryOreFromMod.get(name);
        if (stack != null) return stack.copyWithCount(stackSize);
        logger.warn("There is no item called '{}'", name);
        return ItemStack.EMPTY;
    }

    private boolean contentCreated = false;

    public Eln(IEventBus modBus, ModContainer container) {
        instance = this;

        configureElnLogFile();
        Utils.println(Version.print());

        File configFile = FMLPaths.CONFIGDIR.get().resolve("Eln.cfg").toFile();
        config = new JsonConfig(configFile);
        config.loadConfig();
        config.writeExampleFile();
        FuelRegistry.init(configFile);
        ThermalRegistry.init(configFile);
        MqttManager.init();
        MetricsSubsystem.refreshFromConfig();

        simulator = new Simulator(
            0.05,
            1 / config.getDoubleOrElse("simulation.electrical.frequency", 20.0),
            config.getIntOrElse("simulation.electrical.interSystemOverSampling", 50),
            1 / config.getDoubleOrElse("simulation.thermal.frequency", 400.0)
        );
        nodeManager = new NodeManager();
        ghostManager = new GhostManager();
        delayedTask = new DelayedTaskManager();
        nodeServer = new NodeServer();
        if (FMLEnvironment.dist.isClient()) clientLiveDataManager = new LiveDataManager();
        packetHandler = new PacketHandler();
        serverEventListener = new ServerEventListener();

        // Registries open in an unspecified order; everything is created when the first one does.
        modBus.addListener(RegisterEvent.class, event -> {
            if (!contentCreated) {
                contentCreated = true;
                createContent();
            }
            ElnRegistry.INSTANCE.onRegister(event);
        });
        modBus.addListener(RegisterPayloadHandlersEvent.class, ElnPayload.Companion::register);
        modBus.addListener(RegisterCapabilitiesEvent.class, Eln::registerCapabilities);
        modBus.addListener(EntityAttributeCreationEvent.class, ElnEntities.INSTANCE::onAttributes);
        modBus.addListener(GatherDataEvent.class, event -> ElnDataGenerator.INSTANCE.gather(event));
        modBus.addListener(FMLCommonSetupEvent.class, event -> {
            AnalyticsHandler.INSTANCE.submitUpstreamAnalytics();
            AnalyticsHandler.INSTANCE.submitAgeSeriesAnalytics();
        });
        if (FMLEnvironment.dist.isClient()) {
            ClientProxy.register(modBus);
            if (!FMLEnvironment.production && Boolean.getBoolean("eln.clientSmokeTest")) ClientSmokeTest.start();
        }

        NeoForge.EVENT_BUS.register(RecipeInjector.INSTANCE);
        NeoForge.EVENT_BUS.register(new RoomThermalBlockEventsHandler());
        NeoForge.EVENT_BUS.register(new ElectricMinecartChargeReporter());
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * Creates and queues all blocks, items and so on.
     */
    private void createContent() {
        creativeTabPowerElectronics = new GenericCreativeTab("ElnPowerElectronics", Items.REDSTONE);
        creativeTabCables = new GenericCreativeTab("ElnCables", Items.STRING);
        creativeTabPowerDistribution = new GenericCreativeTab("ElnPowerDistribution", Items.STRING);
        creativeTabSignalProcessing = new GenericCreativeTab("ElnSignalProcessing", Items.COMPARATOR);
        creativeTabLighting = new GenericCreativeTab("ElnLighting", Items.REDSTONE_LAMP);
        creativeTabToolsArmor = new GenericCreativeTab("ElnToolsArmor", Items.IRON_PICKAXE);
        creativeTabOresMaterials = new GenericCreativeTab("ElnOresMaterials", Items.IRON_INGOT);
        creativeTabMachines = new GenericCreativeTab("ElnMachines", Items.DISPENSER);
        creativeTabCreative = new GenericCreativeTab("ElnCreative", Items.NETHER_STAR);
        creativeTabOther = creativeTabOresMaterials;
        creativeTab = creativeTabOther;

        TR_GROUP("ElnPowerElectronics", "Electrical Age - Power Electronics");
        TR_GROUP("ElnSignalProcessing", "Electrical Age - Signal Processing");
        TR_GROUP("ElnLighting", "Electrical Age - Lighting");
        TR_GROUP("ElnCables", "Electrical Age - Cables");
        TR_GROUP("ElnPowerDistribution", "Electrical Age - Power Distribution");
        TR_GROUP("ElnToolsArmor", "Electrical Age - Tools & Armor");
        TR_GROUP("ElnOresMaterials", "Electrical Age - Ores & Materials");
        TR_GROUP("ElnMachines", "Electrical Age - Machines");
        TR_GROUP("ElnCreative", "Electrical Age - Creative");

        arcClayBlock = ElnRegistry.registerBlockWithItem("arc_clay_block", new ArcClayBlock());
        arcMetalBlock = ElnRegistry.registerBlockWithItem("arc_metal_block", new ArcMetalBlock());
        creativeTabOresMaterials.add(() -> new ItemStack(arcClayBlock));
        creativeTabOresMaterials.add(() -> new ItemStack(arcMetalBlock));

        transparentNodeBlock = ElnRegistry.registerBlock("transparent_node", new TransparentNodeBlock());
        sixNodeBlock = ElnRegistry.registerBlock("six_node", new SixNodeBlock());
        ghostBlock = ElnRegistry.registerBlock("ghost_block", new GhostBlock());
        lightBlock = ElnRegistry.registerBlock("light_block", new LightBlock());
        ElnBlockEntities.register();

        sharedItem = new SharedItem(64);
        sharedItemStackOne = new SharedItem(1);
        sixNodeItem = new SixNodeItem();
        transparentNodeItem = new TransparentNodeItem();
        oreItem = new OreItem();

        obj.loadAllElnModels();

        NodeManager.registerUuid(sixNodeBlock.getNodeUuid(), SixNode.class);
        NodeManager.registerUuid(transparentNodeBlock.getNodeUuid(), TransparentNode.class);

        SixNode.sixNodeCacheList.add(new SixNodeCacheStd());

        LampLists.translateLampTypes(); // This MUST be called before block/item registration!

        SingleNodeRegistration.INSTANCE.registerSingle();
        SixNodeRegistration.INSTANCE.registerSix();
        TransparentNodeRegistration.INSTANCE.registerTransparent();
        ItemRegistration.INSTANCE.registerItem();
        FluidRegistrationKt.registerElnFluids();
        ElnEntities.register();
        ConfigPlacement.Companion.register();
        ElnRegistry.register(Registries.MENU, "node", GuiHandler.menuType);

        OreDict.registerOre("blockAluminum", arcClayBlock);
        OreDict.registerOre("blockSteel", arcMetalBlock);

        updateCreativeTabIcons();
        for (GenericCreativeTab tab : List.of(creativeTabCables, creativeTabPowerElectronics, creativeTabPowerDistribution,
            creativeTabSignalProcessing, creativeTabLighting, creativeTabMachines, creativeTabToolsArmor,
            creativeTabOresMaterials, creativeTabCreative)) {
            ElnRegistry.register(Registries.CREATIVE_MODE_TAB, Mc.registryPath(tab.getLabel()), tab.build());
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ElnBlockEntities.getTransparentNodeWithFluid(),
            (entity, side) -> entity.fluidHandler(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ElnBlockEntities.getTransparentNode(),
            (entity, side) -> new SidedInvWrapper(entity, side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ElnBlockEntities.getTransparentNodeWithFluid(),
            (entity, side) -> new SidedInvWrapper(entity, side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SingleNodeRegistration.getEnergyConverter(),
            (entity, side) -> entity.getEnergyStorage());
        if (Other.getCcLoaded()) ComputerCraftIntegration.INSTANCE.register(event);
    }

    private void configureElnLogFile() {
        File logDir = FMLPaths.GAMEDIR.get().resolve("logs").toFile();
        if (!logDir.exists() && !logDir.mkdirs()) return;

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        final String appenderName = "ELN_FILE";
        if (configuration.getAppenders().containsKey(appenderName)) return;

        PatternLayout layout = PatternLayout.newBuilder()
            .withPattern("[%d{HH:mm:ss}] [%t/%level] [%logger]: %msg%n")
            .withConfiguration(configuration)
            .build();

        FileAppender appender = FileAppender.newBuilder()
            .setName(appenderName)
            .withFileName(new File(logDir, "eln.log").getAbsolutePath())
            .withAppend(true)
            .setLayout(layout)
            .setConfiguration(configuration)
            .build();

        appender.start();

        LoggerConfig loggerConfig = new LoggerConfig("ELN", Level.INFO, true);
        loggerConfig.addAppender(appender, Level.INFO, null);
        configuration.addLogger("ELN", loggerConfig);
        context.updateLoggers();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent ev) {
        TutorialSignElement.resetBalise();
        if (modbusServer != null) {
            modbusServer.destroy();
            modbusServer = null;
        }
        LightBlockEntity.observers.clear();
        NodeBlockEntity.clientList.clear();
        TeleporterElement.teleporterList.clear();
        IWirelessSignalSpot.spots.clear();
        if (clientLiveDataManager != null) clientLiveDataManager.stop();
        nodeManager.clear();
        ghostManager.clear();
        saveConfig = null;
        modbusServer = null;
        delayedTask.clear();
        DelayedBlockRemove.clear();
        serverEventListener.clear();
        nodeServer.stop();
        simulator.stop();
        LampSupplyElement.channelMap.clear();
        WirelessSignalTxElement.channelMap.clear();
        MqttManager.shutdown();
        MetricsSubsystem.shutdown();
    }

    @SubscribeEvent
    public void onServerStart(ServerAboutToStartEvent ev) {
        modbusServer = new ModbusTcpServer(config.getIntOrElse("integrations.modbus.port", 1502));
        TeleporterElement.teleporterList.clear();
        LightBlockEntity.observers.clear();
        WirelessSignalTxElement.channelMap.clear();
        LampSupplyElement.channelMap.clear();
        if (clientLiveDataManager != null) clientLiveDataManager.start();
        simulator.init();
        simulator.addSlowProcess(wind = new WindProcess());

        if (config.getBooleanOrElse("entities.replicator.enabled", false)) simulator.addSlowProcess(new ReplicatorPopProcess());
        simulator.addSlowProcess(itemEnergyInventoryProcess = new ItemEnergyInventoryProcess());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent ev) {
        saveConfig = SaveConfig.load(ev.getServer());
        nodeServer.init();
        OreScannerManager.regenOreScannerFactors();
        BiomeClimateService.auditMissingBiomeProfilesAtStartup();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent ev) {
        ElnConsoleCommands.register(ev.getDispatcher());
    }

    public double LVP() {
        return 1000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }
    public double MVP() {
        return 2000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }
    public double HVP() {
        return 5000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }
    public double VVP() {
        return 15000 * config.getDoubleOrElse("balance.cables.powerFactor", 1.0);
    }

    private void updateCreativeTabIcons() {
        setTabIcon(creativeTabPowerElectronics, sixNodeItem, meta(33, 1));
        setTabIcon(creativeTabSignalProcessing, sixNodeItem, meta(32, 0));
        setTabIcon(creativeTabLighting, sharedItem, meta(4, 37));
        setTabIcon(creativeTabCables, sixNodeItem, meta(34, 2));
        setTabIcon(creativeTabToolsArmor, sharedItem, meta(14, 0));
        setTabIcon(creativeTabOresMaterials, sharedItem, meta(8, 7));
        setTabIcon(creativeTabMachines, transparentNodeItem, meta(33, 4));
        setTabIcon(creativeTabCreative, sixNodeItem, meta(3, 0));
    }

    private void setTabIcon(GenericCreativeTab tab, DescriptorCollection<?> family, int number) {
        ItemStack stack = family.stackOf(number);
        if (stack != null) tab.setIcon(stack);
    }

    private static int meta(int group, int subId) {
        return subId + (group << 6);
    }

    public boolean isDevelopmentRun() {
        return !FMLEnvironment.production;
    }
}

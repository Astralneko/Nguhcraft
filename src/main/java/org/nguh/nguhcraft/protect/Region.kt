package org.nguh.nguhcraft.protect

import net.minecraft.nbt.NbtCompound
import net.minecraft.network.RegistryByteBuf
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.nguh.nguhcraft.Constants
import kotlin.math.max
import kotlin.math.min

/** A protected region. */
class Region(
    /** Region name. */
    val Name: String,
    FromX: Int,
    FromZ: Int,
    ToX: Int,
    ToZ: Int,
) {
    /**
    * Flags.
    *
    * These dictate what is allowed. Unset means deny.
    */
    enum class Flags {
        /** Allow attacking non-hostile entities. */
        ATTACK_FRIENDLY,

        /** Allow attacking players. */
        ATTACK_PLAYERS,

        /** Allow breaking and placing blocks. */
        CHANGE_BLOCKS,

        /**
        * Allow interacting with entities.
        *
        * Setting this to ALLOW will allow ALL entity interactions; to
        * only allow specific ones, use the flags below instead (e.g.
        * USE_VEHICLES).
        */
        ENTITY_INTERACT,

        /**
        * Allow entities to be affected by the environment.
        *
        * This includes explosions, lightning, etc and any other
        * environmental hazards and everything that is not another
        * entity (even if it is caused by an entity, e.g. a creeper
        * exploding).
        */
        ENVIRONMENTAL_HAZARDS,

        /**
        * Allow teleportation.
        *
        * This restricts the use of ender pearls and chorus fruit, but NOT
        * the /tp command, command blocks, or other forms of hard-coded
        * teleporting (endermen etc.).
        */
        TELEPORT,

        /** Allow trading with villagers. */
        TRADE,

        /**
        * Allow using and destroying vehicles.
        *
        * This is one permission because e.g. using minecarts without
        * being able to place or destroy them is fairly useless.
        */
        USE_VEHICLES;

        /** Get the bit mask for this flag. */
        fun Bit() = 1L shl ordinal
    }

    /** Flags that are set for this region. */
    private var RegionFlags: Long = 0

    /** Bounds of the region. */
    var MinX: Int = min(FromX, ToX); private set
    var MinZ: Int = min(FromZ, ToZ); private set
    var MaxX: Int = max(FromX, ToX); private set
    var MaxZ: Int = max(FromZ, ToZ); private set

    /** Display this region’s stats. */
    val Stats: Text get() {
        val S = Text.empty()
        Flags.entries.forEach {
            val Status = if (Test(it)) Text.literal("allow").formatted(Formatting.GREEN)
            else Text.literal("deny").formatted(Formatting.RED)
            S.append("\n -")
                .append(Text.literal(it.name.lowercase()).withColor(Constants.Orange))
                .append(": ")
                .append(Status)
        }
        return S
    }

    /** Deserialise a region. */
    constructor(Tag: NbtCompound) : this(
        Tag.getString(TAG_NAME),
        FromX = Tag.getInt(TAG_MIN_X),
        FromZ = Tag.getInt(TAG_MIN_Z),
        ToX = Tag.getInt(TAG_MAX_X),
        ToZ = Tag.getInt(TAG_MAX_Z)
    ) {
        if (Name.isEmpty()) throw IllegalArgumentException("Region name cannot be empty!")
        val FlagsTag = Tag.getCompound(TAG_FLAGS)
        RegionFlags = Flags.entries.fold(0L) { Acc, Flag ->
            if (FlagsTag.getBoolean(Flag.name.lowercase())) Acc or Flag.Bit() else Acc
        }
    }

    /** Deserialise a region from a packet. */
    constructor(buf: RegistryByteBuf) : this(
        Name = buf.readString(),
        FromX = buf.readInt(),
        FromZ = buf.readInt(),
        ToX = buf.readInt(),
        ToZ = buf.readInt()
    ) { RegionFlags = buf.readLong() }

    /** Check if this region allows players to attack non-hostile mobs. */
    fun AllowsAttackingFriendlyEntities() = Test(Flags.ATTACK_FRIENDLY)

    /** Check if this region allows block breaking. */
    fun AllowsBlockModification() = Test(Flags.CHANGE_BLOCKS)

    /** Check if this region allows entity interaction. */
    fun AllowsEntityInteraction() = Test(Flags.ENTITY_INTERACT)

    /** Check if this region allows entities to be affected by the environment. */
    fun AllowsEnvironmentalHazards() = Test(Flags.ENVIRONMENTAL_HAZARDS)

    /** Check if this region allows players to be attacked. */
    fun AllowsPvP() = Test(Flags.ATTACK_PLAYERS)

    /** Check if this region allows teleportation. */
    fun AllowsTeleportation() = Test(Flags.TELEPORT)

    /** Check if this region allows vehicle use. */
    fun AllowsVehicleUse() = Test(Flags.ENTITY_INTERACT) || Test(Flags.USE_VEHICLES)

    /** Check if this region allows trading with villagers. */
    fun AllowsVillagerTrading() = Test(Flags.ENTITY_INTERACT) || Test(Flags.TRADE)

    /** Check if this region contains a block. */
    fun Contains(Pos: BlockPos): Boolean {
        val X = Pos.x
        val Z = Pos.z
        return X in MinX..MaxX && Z in MinZ..MaxZ
    }

    /** Save this region. */
    fun Save(): NbtCompound {
        val Tag = NbtCompound()
        Tag.putString(TAG_NAME, Name)
        Tag.putInt(TAG_MIN_X, MinX)
        Tag.putInt(TAG_MIN_Z, MinZ)
        Tag.putInt(TAG_MAX_X, MaxX)
        Tag.putInt(TAG_MAX_Z, MaxZ)

        // Store flags as strings for robustness.
        val FlagsTag = NbtCompound()
        Flags.entries.forEach { FlagsTag.putBoolean(it.name.lowercase(), Test(it)) }
        Tag.put(TAG_FLAGS, FlagsTag)

        return Tag
    }

    /** Set a region flag. */
    fun SetFlag(Flag: Flags, Allow: Boolean) {
        val OldFlags = RegionFlags
        RegionFlags = if (Allow) OldFlags or Flag.Bit() else OldFlags and Flag.Bit().inv()
        if (OldFlags != RegionFlags) ProtectionManager.Sync()
    }

    /** Helper to simplify testing flags. */
    private fun Test(Flag: Flags) = RegionFlags and Flag.Bit() != 0L

    /** Write this region to a packet. */
    fun Write(buf: RegistryByteBuf) {
        buf.writeString(Name)
        buf.writeInt(MinX)
        buf.writeInt(MinZ)
        buf.writeInt(MaxX)
        buf.writeInt(MaxZ)
        buf.writeLong(RegionFlags)
    }

    /** Get a string representation of this region. */
    override fun toString(): String {
        return "Region($Name, [$MinX, $MinZ] -> [$MaxX, $MaxZ]): $RegionFlags"
    }

    companion object {
        private const val TAG_MIN_X = "MinX"
        private const val TAG_MIN_Z = "MinZ"
        private const val TAG_MAX_X = "MaxX"
        private const val TAG_MAX_Z = "MaxZ"
        private const val TAG_FLAGS = "RegionFlags"
        private const val TAG_NAME = "Name"
    }
}
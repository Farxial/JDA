/*
 *     Copyright 2015-2018 Austin Keener & Michael Ritter & Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.entities;

import gnu.trove.map.TLongObjectMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.managers.ChannelManager;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.managers.ChannelManagerImpl;
import net.dv8tion.jda.internal.requests.AbstractRestAction;
import net.dv8tion.jda.internal.requests.Route;
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl;
import net.dv8tion.jda.internal.requests.restaction.InviteActionImpl;
import net.dv8tion.jda.internal.requests.restaction.PermissionOverrideActionImpl;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.cache.UpstreamReference;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public abstract class AbstractChannelImpl<T extends GuildChannel, M extends AbstractChannelImpl<T, M>> implements GuildChannel
{
    protected final long id;
    protected final UpstreamReference<GuildImpl> guild;

    protected final TLongObjectMap<PermissionOverride> overrides = MiscUtil.newLongMap();

    protected final ReentrantLock mngLock = new ReentrantLock();
    protected volatile ChannelManagerImpl manager;

    protected long parentId;
    protected String name;
    protected int rawPosition;

    public AbstractChannelImpl(long id, GuildImpl guild)
    {
        this.id = id;
        this.guild = new UpstreamReference<>(guild);
    }

    @Override
    public abstract ChannelAction<T> createCopy(Guild guild);

    @Override
    public ChannelAction<T> createCopy()
    {
        return createCopy(getGuild());
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public GuildImpl getGuild()
    {
        return guild.get();
    }

    @Override
    public Category getParent()
    {
        return getGuild().getCategoriesView().get(parentId);
    }

    @Override
    public int getPositionRaw()
    {
        return rawPosition;
    }

    @Override
    public JDA getJDA()
    {
        return getGuild().getJDA();
    }

    @Override
    public PermissionOverride getPermissionOverride(Member member)
    {
        return member != null ? overrides.get(member.getUser().getIdLong()) : null;
    }

    @Override
    public PermissionOverride getPermissionOverride(Role role)
    {
        return role != null ? overrides.get(role.getIdLong()) : null;
    }

    @Override
    public List<PermissionOverride> getPermissionOverrides()
    {
        // already unmodifiable!
        return Arrays.asList(overrides.values(new PermissionOverride[overrides.size()]));
    }

    @Override
    public List<PermissionOverride> getMemberPermissionOverrides()
    {
        return Collections.unmodifiableList(getPermissionOverrides().stream()
                .filter(PermissionOverride::isMemberOverride)
                .collect(Collectors.toList()));
    }

    @Override
    public List<PermissionOverride> getRolePermissionOverrides()
    {
        return Collections.unmodifiableList(getPermissionOverrides().stream()
                .filter(PermissionOverride::isRoleOverride)
                .collect(Collectors.toList()));
    }

    @Override
    public ChannelManager getManager()
    {
        ChannelManagerImpl mng = manager;
        if (mng == null)
        {
            mng = MiscUtil.locked(mngLock, () ->
            {
                if (manager == null)
                    manager = new ChannelManagerImpl(this);
                return manager;
            });
        }
        return mng;
    }

    @Override
    public AuditableRestActionImpl<Void> delete()
    {
        checkPermission(Permission.MANAGE_CHANNEL);

        Route.CompiledRoute route = Route.Channels.DELETE_CHANNEL.compile(getId());
        return new AuditableRestActionImpl<>(getJDA(), route);
    }

    @Override
    public PermissionOverrideActionImpl createPermissionOverride(Member member)
    {
        Checks.notNull(member, "member");
        if (overrides.containsKey(member.getUser().getIdLong()))
            throw new IllegalStateException("Provided member already has a PermissionOverride in this channel!");

        return putPermissionOverride(member);
    }

    @Override
    public PermissionOverrideActionImpl createPermissionOverride(Role role)
    {
        Checks.notNull(role, "role");
        if (overrides.containsKey(role.getIdLong()))
            throw new IllegalStateException("Provided role already has a PermissionOverride in this channel!");

        return putPermissionOverride(role);
    }

    @Override
    public PermissionOverrideActionImpl putPermissionOverride(Member member)
    {
        checkPermission(Permission.MANAGE_PERMISSIONS);
        Checks.notNull(member, "member");

        if (!getGuild().equals(member.getGuild()))
            throw new IllegalArgumentException("Provided member is not from the same guild as this channel!");
        Route.CompiledRoute route = Route.Channels.CREATE_PERM_OVERRIDE.compile(getId(), member.getUser().getId());
        return new PermissionOverrideActionImpl(getJDA(), route, this, member);
    }

    @Override
    public PermissionOverrideActionImpl putPermissionOverride(Role role)
    {
        checkPermission(Permission.MANAGE_PERMISSIONS);
        Checks.notNull(role, "role");

        if (!getGuild().equals(role.getGuild()))
            throw new IllegalArgumentException("Provided role is not from the same guild as this channel!");
        Route.CompiledRoute route = Route.Channels.CREATE_PERM_OVERRIDE.compile(getId(), role.getId());
        return new PermissionOverrideActionImpl(getJDA(), route, this, role);
    }

    @Override
    public InviteAction createInvite()
    {
        if (!this.getGuild().getSelfMember().hasPermission(this, Permission.CREATE_INSTANT_INVITE))
            throw new InsufficientPermissionException(Permission.CREATE_INSTANT_INVITE);

        return new InviteActionImpl(this.getJDA(), this.getId());
    }

    @Override
    public RestAction<List<Invite>> getInvites()
    {
        if (!this.getGuild().getSelfMember().hasPermission(this, Permission.MANAGE_CHANNEL))
            throw new InsufficientPermissionException(Permission.MANAGE_CHANNEL);

        final Route.CompiledRoute route = Route.Invites.GET_CHANNEL_INVITES.compile(getId());

        JDAImpl jda = (JDAImpl) getJDA();
        return new AbstractRestAction<>(jda, route, (response, request) ->
        {
            EntityBuilder entityBuilder = jda.getEntityBuilder();
            JSONArray array = response.getArray();
            List<Invite> invites = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++)
                invites.add(entityBuilder.createInvite(array.getJSONObject(i)));
            return Collections.unmodifiableList(invites);
        });
    }

    @Override
    public long getIdLong()
    {
        return id;
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(id);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof GuildChannel))
            return false;
        if (obj == this)
            return true;
        GuildChannel channel = (GuildChannel) obj;
        return channel.getIdLong() == getIdLong();
    }

    public TLongObjectMap<PermissionOverride> getOverrideMap()
    {
        return overrides;
    }

    @SuppressWarnings("unchecked")
    public M setName(String name)
    {
        this.name = name;
        return (M) this;
    }

    @SuppressWarnings("unchecked")
    public M setParent(long parentId)
    {
        this.parentId = parentId;
        return (M) this;
    }

    @SuppressWarnings("unchecked")
    public M setPosition(int rawPosition)
    {
        this.rawPosition = rawPosition;
        return (M) this;
    }

    protected void checkPermission(Permission permission) {checkPermission(permission, null);}
    protected void checkPermission(Permission permission, String message)
    {
        if (!getGuild().getSelfMember().hasPermission(this, permission))
        {
            if (message != null)
                throw new InsufficientPermissionException(permission, message);
            else
                throw new InsufficientPermissionException(permission);
        }
    }
}

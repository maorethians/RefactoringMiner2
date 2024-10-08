package org.jgroups.auth;

import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.annotations.Property;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Bits;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * <p>
 * The FixedMemberShipToken object predefines a list of IP addresses and ports that can join the group.
 * </p>
 * <p>
 * Configuration parameters for this example are shown below:
 * </p>
 * <ul>
 * <li>fixed_members_value (required) = List of IP addresses & ports (optionally) - ports must be
 * seperated by a '/' e.g. 127.0.0.1/1010*127.0.0.1/4567</li>
 * <li>fixed_members_seperator (required) = The seperator used between IP addresses - e.g. *</li>
 * </ul>
 * 
 * @author Chris Mills (millsy@jboss.com)
 */
public class FixedMembershipToken extends AuthToken {
    private final List<InetSocketAddress> memberList = new ArrayList<>();
    private String                        token = "emptyToken";

    @Property
    private String fixed_members_seperator = ",";

    public FixedMembershipToken() {
    }

    public FixedMembershipToken(String token) {
        this.token=token;
    }

    public String getName() {
        return "org.jgroups.auth.FixedMembershipToken";
    }

    @Property
    public void setFixedMembersSeparator(String value) {
        fixed_members_seperator = value;
    }

    public boolean authenticate(AuthToken token, Message msg) {
        if ((token != null) && (token instanceof FixedMembershipToken) && (this.memberList != null)) {
            PhysicalAddress src = (PhysicalAddress) auth.down(new Event(Event.GET_PHYSICAL_ADDRESS, msg.getSrc()));
            if (src == null) {
                log.error("didn't find physical address for " + msg.getSrc());
                return false;
            }
            return isInMembersList((IpAddress)src);
        }

        if (log.isWarnEnabled())
            log.warn("Invalid AuthToken instance - wrong type or null");
        return false;
    }

    public boolean isInMembersList(IpAddress sender) {
        if(memberList == null || sender == null)
            return false;

        for(InetSocketAddress addr: memberList) {
            if(match(sender, addr))
                return true;
        }
        return false;
    }

    public static boolean match(IpAddress sender, InetSocketAddress addr) {
        return !(sender == null || addr == null)
          && addr.getAddress().equals(sender.getIpAddress())
          && (addr.getPort() == 0 || addr.getPort() == sender.getPort());
    }


    private static boolean hasPort(String member) {
        return member.contains(":");
    }

    @Property(name = "fixed_members_value")
    public void setMemberList(String list) throws UnknownHostException {
        memberList.clear();
        StringTokenizer memberListTokenizer = new StringTokenizer(list, fixed_members_seperator);
        while (memberListTokenizer.hasMoreTokens()) {
            String tmp=memberListTokenizer.nextToken().trim();
            int index=tmp.lastIndexOf('/');
            int port=index != -1? Integer.parseInt(tmp.substring(index+1)) : 0;
            String addr_str=index != -1? tmp.substring(0, index) : tmp;
            InetAddress addr=InetAddress.getByName(addr_str);
            memberList.add(new InetSocketAddress(addr, port));
        }
    }

    /**
     * Required to serialize the object to pass across the wire
     * @param out
     * @throws java.io.IOException
     */
    public void writeTo(DataOutput out) throws Exception {
        if (log.isDebugEnabled())
            log.debug("SimpleToken writeTo()");
        Bits.writeString(this.token,out);
    }

    /**
     * Required to deserialize the object when read in from the wire
     * @param in
     * @throws Exception
     */
    public void readFrom(DataInput in) throws Exception {
        if (log.isDebugEnabled())
            log.debug("SimpleToken readFrom()");
        this.token = Bits.readString(in);
    }

    public int size() {
        return Util.size(token);
    }
}

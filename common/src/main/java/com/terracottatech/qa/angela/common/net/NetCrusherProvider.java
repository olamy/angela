package com.terracottatech.qa.angela.common.net;

import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Net Crusher based DisruptionProvider.
 * <p>
 * https://github.com/NetCrusherOrg/netcrusher-java
 */
public class NetCrusherProvider implements DisruptionProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(NetCrusherProvider.class);

  private final Map<Link, DisruptorLinkImpl> links = new HashMap<>();

  @Override
  public boolean isProxyBased() {
    return true;
  }

  @Override
  public Disruptor createLink(InetSocketAddress src, InetSocketAddress dest) {
    LOGGER.debug("creating link between source {} and destination {}", src, dest);
    synchronized (links) {
      Link link = new Link(src, dest);
      DisruptorLinkImpl existing = links.get(link);
      if (existing == null) {
        existing = new DisruptorLinkImpl(link);
        links.put(link, existing);
      }
      return existing;
    }
  }


  @Override
  public void removeLink(Disruptor disruptor) {
    try {
      disruptor.close();
    } catch (Exception e) {
      LOGGER.error("Error when closing {} {} ", disruptor, e);
    } finally {
      synchronized (links) {
        links.remove(((DisruptorLinkImpl) disruptor).getLink());
      }
    }
  }

  /**
   * Support only partition(disrupt) for now
   */
  private static class DisruptorLinkImpl implements Disruptor {
    private final NioReactor reactor;
    private final TcpCrusher crusher;
    private final Link link;
    private volatile DisruptorState state;

    public DisruptorLinkImpl(Link link) {
      this.link = link;
      try {
        reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(link.getSource())
            .withConnectAddress(link.getDestination())
            .buildAndOpen();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      state = DisruptorState.UNDISRUPTED;
    }


    @Override
    public void disrupt() {
      if (state != DisruptorState.UNDISRUPTED) {
        throw new IllegalStateException("illegal state " + state);
      }
      LOGGER.debug("blocking {} ", this);
      crusher.freeze();
      state = DisruptorState.DISRUPTED;
    }


    @Override
    public void undisrupt() {
      if (state != DisruptorState.DISRUPTED) {
        throw new IllegalStateException("illegal state " + state);
      }
      crusher.unfreeze();
      state = DisruptorState.UNDISRUPTED;
    }

    Link getLink() {
      return link;
    }

    @Override
    public void close() {
      if (state == DisruptorState.DISRUPTED) {
        undisrupt();
      }
      if (state == DisruptorState.UNDISRUPTED) {
        LOGGER.debug("closing {}", this);
        crusher.close();
        reactor.close();
        state = DisruptorState.CLOSED;
      }
    }

    @Override
    public String toString() {
      return "DisruptorLinkImpl{" +
          "link=" + link +
          ", state=" + state +
          '}';
    }
  }

}

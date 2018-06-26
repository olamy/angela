package com.terracottatech.qa.angela.common.net;

import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Net Crusher based DisruptionProvider.
 * <p>
 * https://github.com/NetCrusherOrg/netcrusher-java
 */
public class NetCrusherProvider implements DisruptionProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(NetCrusherProvider.class);
  private final Map<Disruptor, Disruptor> links = new ConcurrentHashMap<>();

  @Override
  public boolean isProxyBased() {
    return true;
  }

  @Override
  public Disruptor createLink(InetSocketAddress src, InetSocketAddress dest) {
    LOGGER.debug("creating link between source {} and destination {}", src, dest);
    return links.computeIfAbsent(new DisruptorLinkImpl(src, dest), link -> link);
  }


  @Override
  public void removeLink(Disruptor link) {
    try {
      link.close();
    } catch (Exception e) {
      LOGGER.error("Error when closing {} {} ", link, e);
    } finally {
      links.remove(link);
    }

  }

  /**
   * Support only partition(disrupt) for now
   */
  private static class DisruptorLinkImpl implements Disruptor {
    private final TcpCrusher crusher;
    private final InetSocketAddress source;
    private final InetSocketAddress destination;

    private volatile DisruptorState state;

    public DisruptorLinkImpl(final InetSocketAddress source, final InetSocketAddress destination) {
      this.source = source;
      this.destination = destination;
      try {
        NioReactor reactor = new NioReactor();

        crusher = TcpCrusherBuilder.builder()
            .withReactor(reactor)
            .withBindAddress(source)
            .withConnectAddress(destination)
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
      if (state == DisruptorState.DISRUPTED) {
        LOGGER.debug("undisrupting {}", this);
        crusher.unfreeze();
      }
      state = DisruptorState.UNDISRUPTED;
    }

    @Override
    public void close() throws Exception {
      if (state != DisruptorState.CLOSED) {
        LOGGER.debug("closing {}", this);
        crusher.close();
      }
      state = DisruptorState.CLOSED;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DisruptorLinkImpl that = (DisruptorLinkImpl)o;

      if (!source.equals(that.source)) return false;
      return destination.equals(that.destination);
    }

    @Override
    public int hashCode() {
      int result = source.hashCode();
      result = 31 * result + destination.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "DisruptorLinkImpl{" +
             "source=" + source +
             ", destination=" + destination +
             '}';
    }
  }

}

const serializeCandidate = (candidate) => {
  if (!candidate) return null;
  if (typeof candidate.toJSON === "function") return candidate.toJSON();
  return { candidate: candidate.candidate, sdpMid: candidate.sdpMid, sdpMLineIndex: candidate.sdpMLineIndex, usernameFragment: candidate.usernameFragment };
};

/** Small 1:1 WebRTC session; the authenticated WebSocket transports signals. */
export class DirectWebRtcCall {
  constructor({ iceConfig, localStream, sendSignal, onState, onRemoteTrack, onDiagnostic }) {
    if (!iceConfig?.iceServers?.length) throw new Error("缺少 TURN 配置");
    this.sendSignal = sendSignal;
    this.onState = onState || (() => {});
    this.onRemoteTrack = onRemoteTrack || (() => {});
    this.onDiagnostic = onDiagnostic || (() => {});
    this.pendingCandidates = [];
    this.closed = false;
    this.disconnectTimer = null;
    this.pc = new RTCPeerConnection({ iceServers: iceConfig.iceServers, iceTransportPolicy: iceConfig.iceTransportPolicy === "relay" ? "relay" : "all" });
    localStream.getTracks().forEach((track) => this.pc.addTrack(track, localStream));
    this.pc.onicecandidate = ({ candidate }) => {
      if (!candidate || this.closed) return;
      const plain = serializeCandidate(candidate);
      this.onDiagnostic("local-ice", { candidateType: plain?.candidate?.match(/ typ (host|srflx|relay)\b/)?.[1] || "unknown" });
      this.sendSignal("ICE", { candidate: plain });
    };
    this.pc.ontrack = (event) => this.onRemoteTrack(event.streams[0], event.track);
    this.pc.oniceconnectionstatechange = () => this.handleIceState();
  }
  async createOffer() {
    const offer = await this.pc.createOffer();
    await this.pc.setLocalDescription(offer);
    return { type: this.pc.localDescription.type, sdp: this.pc.localDescription.sdp };
  }
  async acceptOffer(offer) {
    await this.pc.setRemoteDescription(offer);
    await this.flushCandidates();
    const answer = await this.pc.createAnswer();
    await this.pc.setLocalDescription(answer);
    return { type: this.pc.localDescription.type, sdp: this.pc.localDescription.sdp };
  }
  async handleSignal(kind, payload) {
    if (this.closed) return false;
    if (kind === "ANSWER") {
      const answer = payload?.sdp;
      if (!answer?.type || !answer?.sdp) throw new Error("收到的应答无效");
      await this.pc.setRemoteDescription(answer);
      await this.flushCandidates();
      return true;
    }
    if (kind === "ICE") {
      const candidate = payload?.candidate;
      if (!candidate) return true;
      if (!this.pc.remoteDescription) this.pendingCandidates.push(candidate);
      else await this.addCandidate(candidate);
      return true;
    }
    return false;
  }
  async addCandidate(candidate) { try { await this.pc.addIceCandidate(candidate); } catch (error) { this.onDiagnostic("remote-ice-error", { message: error?.message || "unknown" }); } }
  async flushCandidates() { for (const candidate of this.pendingCandidates.splice(0)) await this.addCandidate(candidate); }
  handleIceState() {
    const state = this.pc.iceConnectionState;
    this.onDiagnostic("ice-state", { state });
    if (state === "connected" || state === "completed") { clearTimeout(this.disconnectTimer); this.onState("connected"); return; }
    if (state === "failed") { this.onState("failed"); return; }
    if (state === "disconnected") { clearTimeout(this.disconnectTimer); this.disconnectTimer = setTimeout(() => { if (!this.closed && this.pc.iceConnectionState === "disconnected") this.onState("failed"); }, 10_000); }
  }
  close() { this.closed = true; clearTimeout(this.disconnectTimer); this.pc.onicecandidate = null; this.pc.ontrack = null; this.pc.oniceconnectionstatechange = null; try { this.pc.close(); } catch (_) {} }
}

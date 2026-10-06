package com.coldguard.gateway.api.asset;

import com.coldguard.asset.grpc.v1.GetAssetRequest;
import com.coldguard.gateway.infrastructure.AssetGrpcClient;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Assets (cold units). Shape validation, mapping and the call; no business rule. */
@RestController
@RequestMapping("/api/v1/assets")
class AssetController {

  private final AssetGrpcClient asset;

  AssetController(AssetGrpcClient asset) {
    this.asset = asset;
  }

  @GetMapping
  PageResponse<AssetResponse> list(
      @RequestParam(required = false) String siteId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int size) {
    var reply = asset.listAssets(AssetRestMapper.listAssets(siteId, page, size));
    return new PageResponse<>(
        reply.getAssetsList().stream().map(AssetRestMapper::toRest).toList(),
        AssetRestMapper.toRest(reply.getPage()));
  }

  @PostMapping
  ResponseEntity<AssetResponse> register(@Valid @RequestBody RegisterAssetRequest request) {
    AssetResponse created =
        AssetRestMapper.toRest(asset.registerAsset(AssetRestMapper.asset(request)));
    return ResponseEntity.created(URI.create("/api/v1/assets/" + created.id())).body(created);
  }

  @GetMapping("/{assetId}")
  AssetResponse get(@PathVariable String assetId) {
    return AssetRestMapper.toRest(
        asset.getAsset(GetAssetRequest.newBuilder().setAssetId(assetId).build()));
  }

  @PatchMapping("/{assetId}")
  AssetResponse update(
      @PathVariable String assetId, @Valid @RequestBody UpdateAssetRequest request) {
    return AssetRestMapper.toRest(asset.updateAsset(AssetRestMapper.updateAsset(assetId, request)));
  }
}

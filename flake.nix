{
  description = "A very basic flake";

  outputs = { self, nixpkgs }:
    let
      pkgs = nixpkgs.legacyPackages.x86_64-linux.pkgs;
    in {
      devShells.x86_64-linux.default = with pkgs; mkShell {
        buildInputs = [ openjdk19 ];
        };
  };
}
